package com.zia.freshdocs.widget;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Stack;

import org.apache.commons.io.IOUtils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.zia.freshdocs.R;
import com.zia.freshdocs.data.NodeRef;
import com.zia.freshdocs.net.CMIS;
import com.zia.freshdocs.util.URLUtils;

public class CMISAdapter extends ArrayAdapter<NodeRef>
{
	protected static  HashMap<String, Integer> mimeMap = new HashMap<String, Integer>();
	static
	{
		mimeMap.put("application/pdf", R.drawable.pdf);
		mimeMap.put("cmis/folder", R.drawable.folder);
		mimeMap.put("text/plain", R.drawable.txt);
		mimeMap.put(null, R.drawable.document);
	}
	
	private String _currentUuid = null;
	private Stack<String> _stack = new Stack<String>(); 
	private CMIS _cmis;

	public CMISAdapter(Context context, int textViewResourceId, NodeRef[] objects)
	{
		super(context, textViewResourceId, objects);
		refresh();
	}

	public CMISAdapter(Context context, int textViewResourceId)
	{
		super(context, textViewResourceId);
		refresh();
	}

	public void refresh()
	{
		if (_cmis == null)
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this
					.getContext());
			_cmis = new CMIS(prefs.getString("hostname", ""), 
					prefs.getString("username", ""),
					prefs.getString("password", ""), 
					Integer.parseInt(prefs.getString("port", "80")));
			String ticket = _cmis.authenticate();

			if (ticket != null)
			{
				home();
			}
		} 
		else
		{
			getChildren(_currentUuid);
		}
	}
	
	protected void home()
	{
		NodeRef companyHome = _cmis.getCompanyHome();
		
		// Save reference to current entry
		_stack.clear();		
		_currentUuid = companyHome.getContent(); 
		
		// Get Company Home children
		getChildren(_currentUuid);		
	}
	
	public boolean hasPrevious()
	{
		return _stack.size() > 0;
	}
	
	public void previous()
	{
		if(_stack.size() > 0)
		{
			_currentUuid = _stack.pop();
			getChildren(_currentUuid);
		}
	}
	
	public void getChildren(int position)
	{
		NodeRef ref = getItem(position);
		
		if(ref.isFolder())
		{
			_stack.push(_currentUuid);
			_currentUuid = ref.getContent();
			getChildren(_currentUuid);
		}
		else
		{
			Context context = getContext();
			
			// Display the content
			Builder builder = URLUtils.toUriBuilder(ref.getContent());
			builder.appendQueryParameter("alf_ticket", _cmis.getTicket());
			FileOutputStream f =  null;
			
			try
			{
				String name = ref.getName();
				f = context.openFileOutput(name, Context.MODE_WORLD_WRITEABLE);
				URL url = new URL(builder.build().toString());
				URLConnection conn = url.openConnection();
				int nBytes = IOUtils.copy(conn.getInputStream(), f);
				f.close();
				
				// Ask for viewer
				Uri uri = Uri.fromFile(new File(name));
				Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
				viewIntent.setType(ref.getContentType());
				context.startActivity(viewIntent);
			} 
			catch(Exception e)
			{
				Log.e(CMISAdapter.class.getSimpleName(), "", e);
			}
		}
	}
	
	protected void getChildren(String uuid)
	{
		clear();
		
		NodeRef[] nodes = _cmis.getChildren(uuid);
		
		for(int i = 0; i < nodes.length; i++)
		{
			add(nodes[i]);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView textView = (TextView) super.getView(position, convertView, parent);
		NodeRef nodeRef = getItem(position);
		String contentType = nodeRef.getContentType();
		Drawable icon = getDrawableForType(contentType == null ? "cmis/folder" : contentType);
		textView.setCompoundDrawablePadding(5);
		textView.setCompoundDrawables(icon,	null, null, null);
		return textView;
	}
	
	protected Drawable getDrawableForType(String contentType)
	{
		Context context = getContext();
		Resources resources = context.getResources();
		int resId = mimeMap.get(mimeMap.containsKey(contentType) ? contentType : null);
		Drawable icon = resources.getDrawable(resId);
		icon.setBounds(new Rect(0, 0, 44, 44));
		return icon;
	}
}

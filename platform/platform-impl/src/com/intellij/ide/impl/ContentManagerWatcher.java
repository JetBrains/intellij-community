// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ContentManagerWatcher {
  private final ToolWindow myToolWindow;
  private final ContentManager myContentManager;
  private final PropertyChangeListener myPropertyChangeListener;

  public ContentManagerWatcher(ToolWindow toolWindow,ContentManager contentManager) {
    myToolWindow = toolWindow;
    myContentManager = contentManager;
    myToolWindow.setAvailable(contentManager.getContentCount()>0,null);

    myPropertyChangeListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent e) {
      }
    };

    contentManager.addContentManagerListener(
      new ContentManagerAdapter(){
        @Override
        public void contentAdded(ContentManagerEvent e) {
          e.getContent().addPropertyChangeListener(myPropertyChangeListener);
          myToolWindow.setAvailable(true,null);
        }

        @Override
        public void contentRemoved(ContentManagerEvent e) {
          e.getContent().removePropertyChangeListener(myPropertyChangeListener);
          myToolWindow.setAvailable(myContentManager.getContentCount()>0,null);
        }
      }
    );

    // Synchonize title with current state of manager

    for(int i=0;i<myContentManager.getContentCount();i++){
      Content content=myContentManager.getContent(i);
      content.addPropertyChangeListener(myPropertyChangeListener);
    }
  }

}
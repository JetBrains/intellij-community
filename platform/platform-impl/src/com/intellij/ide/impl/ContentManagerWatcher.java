/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      public void propertyChange(PropertyChangeEvent e) {
      }
    };

    contentManager.addContentManagerListener(
      new ContentManagerAdapter(){
        public void contentAdded(ContentManagerEvent e) {
          e.getContent().addPropertyChangeListener(myPropertyChangeListener);
          myToolWindow.setAvailable(true,null);
        }

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
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
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public abstract class PanelWithActionsAndCloseButton extends JPanel implements DataProvider {
  protected final ContentManager myContentManager;
  private final String myHelpId;
  private final DefaultActionGroup myToolbalGroup = new DefaultActionGroup(null, false);

  public PanelWithActionsAndCloseButton(@NotNull ContentManager contentManager, @NonNls String helpId) {
    super(new BorderLayout());
    myContentManager = contentManager;
    myHelpId = helpId;

    myContentManager.addContentManagerListener(new ContentManagerAdapter(){
      public void contentRemoved(ContentManagerEvent event) {
        if (event.getContent().getComponent() == PanelWithActionsAndCloseButton.this) {
          dispose();
          myContentManager.removeContentManagerListener(this);
        }
      }
    });

  }

  public String getHelpId() {
    return myHelpId;
  }

  protected void init(){
    addActionsTo(myToolbalGroup);
    myToolbalGroup.add(new MyCloseAction());
    myToolbalGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));


    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, myToolbalGroup, false)
        .getComponent(), BorderLayout.WEST);
    add(createCenterPanel(), BorderLayout.CENTER);
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)){
      return myHelpId;
    }
    return null;
  }

  protected abstract JComponent createCenterPanel();

  protected void addActionsTo(DefaultActionGroup group){

  }

  protected void dispose(){

  }

  private class MyCloseAction extends AnAction implements DumbAware {
    public MyCloseAction() {
      super(CommonBundle.message("close.action.name"), null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      Content content = myContentManager.getContent(PanelWithActionsAndCloseButton.this);
      if (content != null) {
        myContentManager.removeContent(content, true);
      }
    }
  }
}

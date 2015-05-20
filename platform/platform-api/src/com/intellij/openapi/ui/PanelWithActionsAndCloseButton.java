/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;


public abstract class PanelWithActionsAndCloseButton extends JPanel implements DataProvider {
  protected final ContentManager myContentManager;
  private final String myHelpId;
  private final boolean myVerticalToolbar;
  private boolean myCloseEnabled;
  private final DefaultActionGroup myToolbarGroup = new DefaultActionGroup(null, false);

  public PanelWithActionsAndCloseButton(ContentManager contentManager, @NonNls String helpId) {
    this(contentManager, helpId, true);
  }

  public PanelWithActionsAndCloseButton(ContentManager contentManager, @NonNls String helpId, final boolean verticalToolbar) {
    super(new BorderLayout());
    myContentManager = contentManager;
    myHelpId = helpId;
    myVerticalToolbar = verticalToolbar;
    myCloseEnabled = true;

    if (myContentManager != null) {
      myContentManager.addContentManagerListener(new ContentManagerAdapter(){
        public void contentRemoved(ContentManagerEvent event) {
          if (event.getContent().getComponent() == PanelWithActionsAndCloseButton.this) {
            dispose();
            myContentManager.removeContentManagerListener(this);
          }
        }
      });
    }

  }

  public String getHelpId() {
    return myHelpId;
  }

  protected void disableClose() {
    myCloseEnabled = false;
  }

  protected void init(){
    addActionsTo(myToolbarGroup);
    myToolbarGroup.add(new MyCloseAction());
    myToolbarGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));


    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, myToolbarGroup, ! myVerticalToolbar);
    JComponent centerPanel = createCenterPanel();
    toolbar.setTargetComponent(centerPanel);
    for (AnAction action : myToolbarGroup.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), centerPanel);
    }

    add(centerPanel, BorderLayout.CENTER);
    if (myVerticalToolbar) {
      add(toolbar.getComponent(), BorderLayout.WEST);
    } else {
      add(toolbar.getComponent(), BorderLayout.NORTH);
    }
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)){
      return myHelpId;
    }
    return null;
  }

  protected abstract JComponent createCenterPanel();

  protected void addActionsTo(DefaultActionGroup group) {}

  protected void dispose() {}

  private class MyCloseAction extends CloseTabToolbarAction {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myCloseEnabled);
    }

    public void actionPerformed(AnActionEvent e) {
      if (myContentManager != null) {
        Content content = myContentManager.getContent(PanelWithActionsAndCloseButton.this);
        if (content != null) {
          if (content instanceof TabbedContent && ((TabbedContent)content).getTabs().size() > 1) {
            final TabbedContent tabbedContent = (TabbedContent)content;
            final JComponent component = content.getComponent();
            tabbedContent.removeContent(component);
            myContentManager.setSelectedContent(content, true, true); //we should request focus here
          } else {
            myContentManager.removeContent(content, true);
          }
        }
      }
    }
  }
}

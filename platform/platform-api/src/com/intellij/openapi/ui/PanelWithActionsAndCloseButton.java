/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.*;
import com.intellij.util.ContentsUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;


public abstract class PanelWithActionsAndCloseButton extends JPanel implements DataProvider, Disposable {
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
            Disposer.dispose(PanelWithActionsAndCloseButton.this);
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
          ContentsUtil.closeContentTab(myContentManager, content);
          if (content instanceof TabbedContent && ((TabbedContent)content).hasMultipleTabs()) {
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

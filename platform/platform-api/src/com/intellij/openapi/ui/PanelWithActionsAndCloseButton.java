// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.ContentsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class PanelWithActionsAndCloseButton extends JPanel implements UiCompatibleDataProvider, Disposable {
  protected final ContentManager myContentManager;
  private final @NonNls String myHelpId;
  private final boolean myVerticalToolbar;
  private boolean myCloseEnabled;
  private final DefaultActionGroup myToolbarGroup = new DefaultActionGroup();

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
      myContentManager.addContentManagerListener(new ContentManagerListener() {
        @Override
        public void contentRemoved(@NotNull ContentManagerEvent event) {
          if (event.getContent().getComponent() == PanelWithActionsAndCloseButton.this) {
            Disposer.dispose(PanelWithActionsAndCloseButton.this);
            myContentManager.removeContentManagerListener(this);
          }
        }
      });
    }
  }

  public @NonNls String getHelpId() {
    return myHelpId;
  }

  protected void disableClose() {
    myCloseEnabled = false;
  }

  protected void init() {
    addActionsTo(myToolbarGroup);
    myToolbarGroup.add(new MyCloseAction());

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar toolbar = actionManager.createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, myToolbarGroup, !myVerticalToolbar);
    JComponent centerPanel = createCenterPanel();
    toolbar.setTargetComponent(centerPanel);
    for (AnAction action : myToolbarGroup.getChildren(actionManager)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), centerPanel);
    }

    add(centerPanel, BorderLayout.CENTER);
    if (myVerticalToolbar) {
      add(toolbar.getComponent(), BorderLayout.WEST);
    }
    else {
      add(toolbar.getComponent(), BorderLayout.NORTH);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, myHelpId);
  }

  protected abstract JComponent createCenterPanel();

  protected void addActionsTo(DefaultActionGroup group) {}

  private class MyCloseAction extends CloseTabToolbarAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myCloseEnabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myContentManager != null) {
        Content content = myContentManager.getContent(PanelWithActionsAndCloseButton.this);
        if (content != null) {
          ContentsUtil.closeContentTab(myContentManager, content);
        }
      }
    }
  }
}
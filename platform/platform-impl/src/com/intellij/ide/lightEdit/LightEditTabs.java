// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

class LightEditTabs extends JBEditorTabs {
  private final LightEditorManager myEditorManager;

  LightEditTabs(@NotNull Disposable parent, LightEditorManager editorManager) {
    super(LightEditUtil.getProject(), ActionManager.getInstance(), null, parent);
    myEditorManager = editorManager;
    addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        onSelectionChange(newSelection);
      }
    });
  }

  void addEditorTab(@NotNull LightEditorInfo editorInfo) {
    TabInfo tabInfo = new TabInfo(new EditorContainer(editorInfo.getEditor()))
      .setText(editorInfo.getFile().getPresentableName())
      .setTabColor(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());

    tabInfo.setObject(editorInfo);

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTabAction(editorInfo));

    tabInfo.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    addTabSilently(tabInfo, -1);
    select(tabInfo, true);
    myEditorManager.fireEditorSelected(editorInfo);
  }

  private void onSelectionChange(@Nullable TabInfo tabInfo) {
    LightEditorInfo selectedEditorInfo = null;
    if (tabInfo != null) {
      Object data = tabInfo.getObject();
      if (data instanceof LightEditorInfo) {
        selectedEditorInfo = (LightEditorInfo)data;
      }
    }
    myEditorManager.fireEditorSelected(selectedEditorInfo);
  }

  void selectTab(@NotNull LightEditorInfo info) {
    getTabs().stream()
      .filter(tabInfo -> tabInfo.getObject().equals(info))
      .findFirst().ifPresent(tabInfo -> select(tabInfo, true));
  }

  private class CloseTabAction extends DumbAwareAction {
    private final LightEditorInfo myEditorInfo;

    private CloseTabAction(@NotNull LightEditorInfo editorInfo) {
      myEditorInfo = editorInfo;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if ((e.getModifiers() & InputEvent.ALT_MASK) == 0) {
        closeCurrentTab();
      }
      else {
        closeAllTabsExceptCurrent();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(getIcon());
      e.getPresentation().setHoveredIcon(AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().getShowCloseButton());
      e.getPresentation().setText("Close. Alt-Click to Close Others.");
    }

    private Icon getIcon() {
      return
        LightEditorManager.isUnsaved(myEditorInfo) ? AllIcons.General.Modified : AllIcons.Actions.Close;
    }

    private void closeCurrentTab() {
      TabInfo tabInfo = findInfo(myEditorInfo);
      if (tabInfo != null) {
        closeTab(tabInfo);
      }
    }

    private void closeAllTabsExceptCurrent() {
      getTabs().stream()
        .filter(tabInfo -> tabInfo != getSelectedInfo())
        .forEach(this::closeTab);
    }

    private void closeTab(@NotNull TabInfo tabInfo) {
      Object data = tabInfo.getObject();
      if (data instanceof LightEditorInfo) {
        if (!LightEditorManager.isUnsaved(myEditorInfo) || LightEditUtil.confirmClose((LightEditorInfo)data)) {
          removeTab(tabInfo).doWhenDone(() -> myEditorManager.closeEditor(myEditorInfo));
        }
      }
    }
  }

  private static class EditorContainer extends JPanel implements DataProvider {

    private EditorContainer(Editor editor) {
      super(new BorderLayout());
      add(editor.getComponent(), BorderLayout.CENTER);
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return LightEditUtil.getProject();
      }
      return null;
    }
  }
}

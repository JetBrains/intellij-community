// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
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
    super(null, ActionManager.getInstance(), null, parent);
    myEditorManager = editorManager;
    addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        onSelectionChange(newSelection);
      }
    });
  }

  void addEditorTab(@NotNull Editor editor, @NotNull VirtualFile file) {
    TabInfo tabInfo = new TabInfo(createEditorContainer(editor))
      .setText(file.getPresentableName())
      .setTabColor(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());

    final LightEditorInfo editorInfo = new LightEditorInfo(editor, file);
    tabInfo.setObject(editorInfo);

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTabAction(editorInfo));

    tabInfo.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    addTabSilently(tabInfo, -1);
    select(tabInfo, true);
    myEditorManager.fireEditorSelected(editorInfo);
  }

  private static JComponent createEditorContainer(@NotNull Editor editor) {
    JPanel editorPanel = new JPanel(new BorderLayout());
    editorPanel.add(editor.getComponent(), BorderLayout.CENTER);
    return editorPanel;
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
      e.getPresentation().setIcon(AllIcons.Actions.Close);
      e.getPresentation().setHoveredIcon(AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().getShowCloseButton());
      e.getPresentation().setText("Close. Alt-Click to Close Others.");
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
      removeTab(tabInfo).doWhenDone(() -> myEditorManager.closeEditor(myEditorInfo));
    }
  }

}

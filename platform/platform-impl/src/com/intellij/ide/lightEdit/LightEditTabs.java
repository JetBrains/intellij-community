// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

final class LightEditTabs extends JBEditorTabs {
  private final LightEditorManagerImpl myEditorManager;

  LightEditTabs(@NotNull Disposable parent, LightEditorManagerImpl editorManager) {
    super(LightEditUtil.getProject(), null, parent);

    myEditorManager = editorManager;
    addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        ObjectUtils.consumeIfNotNull(oldSelection, tabInfo -> tabInfo.setTabColor(getUnselectedTabColor()));
        ObjectUtils.consumeIfNotNull(newSelection, tabInfo -> tabInfo.setTabColor(getSelectedTabColor()));
        onSelectionChange(newSelection);
      }
    });
  }

  private static Color getSelectedTabColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
  }

  private static Color getUnselectedTabColor() {
    return null;
  }

  void addEditorTab(@NotNull LightEditorInfo editorInfo) {
    addEditorTab(editorInfo, -1);
  }

  private void addEditorTab(@NotNull LightEditorInfo editorInfo, int index) {
    TabInfo tabInfo = new TabInfo(new EditorContainer(editorInfo.getEditor()))
      .setText(editorInfo.getFile().getPresentableName())
      .setIcon(getFileTypeIcon(editorInfo));

    tabInfo.setObject(editorInfo);

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTabAction(editorInfo));

    tabInfo.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    addTabSilently(tabInfo, index);
    select(tabInfo, true);
    myEditorManager.fireEditorSelected(editorInfo);
  }

  private static Icon getFileTypeIcon(@NotNull LightEditorInfo editorInfo) {
    return editorInfo.getFile().getFileType().getIcon();
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
      return myEditorInfo.isUnsaved() ? AllIcons.General.Modified : AllIcons.Actions.Close;
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
        final LightEditorInfo editorInfo = (LightEditorInfo)data;
        if (!myEditorInfo.isUnsaved() ||
            autosaveDocument(editorInfo) ||
            LightEditUtil.confirmClose(
              ApplicationBundle.message("light.edit.close.message"),
              ApplicationBundle.message("light.edit.close.title"),
              () -> saveDocument(editorInfo))) {
          removeTab(tabInfo).doWhenDone(() -> myEditorManager.closeEditor(myEditorInfo));
        }
      }
    }

    private boolean autosaveDocument(@NotNull LightEditorInfo editorInfo) {
      if (LightEditService.getInstance().isAutosaveMode()) {
        saveDocument(editorInfo);
        return true;
      }
      return false;
    }
  }

  private void saveDocument(@NotNull LightEditorInfo editorInfo) {
    if (editorInfo.isNew()) {
      VirtualFile targetFile = LightEditUtil.chooseTargetFile(this.getParent(), editorInfo);
      if (targetFile != null) {
        myEditorManager.saveAs(editorInfo, targetFile);
      }
    }
    else {
      FileDocumentManager.getInstance().saveDocument(editorInfo.getEditor().getDocument());
    }
  }

  void replaceTab(@NotNull LightEditorInfo oldInfo, @NotNull LightEditorInfo newInfo) {
    TabInfo oldTabInfo = findInfo(oldInfo);
    if (oldTabInfo != null) {
      int oldIndex = getIndexOf(oldTabInfo);
      if (oldIndex >= 0) {
        removeTab(oldTabInfo).doWhenDone(()->myEditorManager.closeEditor(oldInfo));
        addEditorTab(newInfo, oldIndex);
      }
    }
  }

  @Nullable
  VirtualFile getSelectedFile() {
    TabInfo info = getSelectedInfo();
    if (info != null) {
      LightEditorInfo editorInfo = ObjectUtils.tryCast(info.getObject(), LightEditorInfo.class);
      if (editorInfo != null) {
        return editorInfo.getFile();
      }
    }
    return null;
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

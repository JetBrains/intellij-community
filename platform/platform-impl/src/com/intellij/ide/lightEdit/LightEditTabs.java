// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class LightEditTabs extends JBEditorTabs implements LightEditorListener {
  private final LightEditorManagerImpl myEditorManager;
  private final ExecutorService myTabUpdateExecutor;

  LightEditTabs(@NotNull Disposable parent, LightEditorManagerImpl editorManager) {
    super(LightEditUtil.getProject(), null, parent);

    myEditorManager = editorManager;
    myTabUpdateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Light Edit Tabs Update", 1);
    addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        ObjectUtils.consumeIfNotNull(oldSelection, tabInfo -> tabInfo.setTabColor(null));
        asyncUpdateTab(newSelection);
        onSelectionChange(newSelection);
      }
    });
    myEditorManager.addListener(this, this);
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
    asyncUpdateTab(tabInfo);
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
        removeTab(oldTabInfo).doWhenDone(() -> myEditorManager.closeEditor(oldInfo));
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

  private class EditorContainer extends JPanel implements DataProvider {

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
      else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return getSelectedFile();
      }
      else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return new VirtualFile[] {getSelectedFile()};
      }
      return null;
    }
  }

  private void asyncUpdateTab(@NotNull TabInfo tabInfo) {
    assert ApplicationManager.getApplication().isDispatchThread();
    Object object = tabInfo.getObject();
    if (!(object instanceof LightEditorInfo)) return;
    asyncUpdateTabs(Collections.singletonList(Pair.createNonNull(tabInfo, (LightEditorInfo)object)));
  }

  private void asyncUpdateTabs(@NotNull List<Pair.NonNull<TabInfo, LightEditorInfo>> tabEditorPairs) {
    myTabUpdateExecutor.execute(() -> {
      List<Pair.NonNull<TabInfo, TextAttributes>> tabAttributesPairs = ContainerUtil.map(tabEditorPairs, pair -> {
        return Pair.createNonNull(pair.first, calcAttributes(pair.second));
      });
      ApplicationManager.getApplication().invokeLater(() -> {
        for (Pair.NonNull<TabInfo, TextAttributes> attributesPair : tabAttributesPairs) {
          updateTabPresentation(attributesPair.first, attributesPair.second);
        }
      });
    });
  }

  private void updateTabPresentation(@NotNull TabInfo tabInfo, @NotNull TextAttributes attributes) {
    tabInfo.setDefaultForeground(attributes.getForegroundColor());
    tabInfo.setTabColor(tabInfo == getSelectedInfo() ? attributes.getBackgroundColor() : null);
  }

  @NotNull
  private static TextAttributes calcAttributes(@NotNull LightEditorInfo editorInfo) {
    TextAttributes attributes = new TextAttributes();
    attributes.setBackgroundColor(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    LightEditTabAttributesProvider.EP_NAME.getExtensionList().forEach(
      provider -> {
        TextAttributes provided = provider.calcAttributes(editorInfo);
        if (provided != null) {
          if (provided.getForegroundColor() != null) {
            attributes.setForegroundColor(provided.getForegroundColor());
          }
          if (provided.getBackgroundColor() != null) {
            attributes.setBackgroundColor(provided.getBackgroundColor());
          }
        }
      }
    );
    return attributes;
  }

  @Override
  public void fileStatusChanged(@NotNull Collection<LightEditorInfo> editorInfos) {
    ApplicationManager.getApplication().invokeLater(() -> {
      List<Pair.NonNull<TabInfo, LightEditorInfo>> tabEditorPairs = ContainerUtil.mapNotNull(editorInfos, editorInfo -> {
        TabInfo info = findInfo(editorInfo);
        if (info == null) return null;
        return Pair.createNonNull(info, editorInfo);
      });
      if (!tabEditorPairs.isEmpty()) {
        asyncUpdateTabs(tabEditorPairs);
      }
    });
  }

  @Override
  public void dispose() {
    myTabUpdateExecutor.shutdown();

    super.dispose();
  }
}

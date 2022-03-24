// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.NextTabAction;
import com.intellij.ide.actions.PreviousTabAction;
import com.intellij.ide.lightEdit.project.LightEditFileEditorManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

final class LightEditTabs extends JBEditorTabs implements LightEditorListener, CloseAction.CloseTarget {
  private final @NotNull Project myProject;
  private final @NotNull LightEditorManagerImpl myEditorManager;
  private final @NotNull ExecutorService myTabUpdateExecutor;

  LightEditTabs(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull LightEditorManagerImpl editorManager) {
    super(project, null, parentDisposable);
    myProject = project;

    myEditorManager = editorManager;
    myTabUpdateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Light Edit Tabs Update", 1);
    Disposer.register(parentDisposable, () -> myTabUpdateExecutor.shutdown());
    addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        ObjectUtils.consumeIfNotNull(oldSelection, tabInfo -> tabInfo.setTabColor(null));
        asyncUpdateTab(newSelection);
        onSelectionChange(newSelection);
      }
    });
    myEditorManager.addListener(this, parentDisposable);
    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        revalidateAndRepaint();
      }
    });
  }

  void addEditorTab(@NotNull LightEditorInfo editorInfo) {
    addEditorTab(editorInfo, -1);
  }

  private void addEditorTab(@NotNull LightEditorInfo editorInfo, int index) {
    EditorComposite editorContainer = ((LightEditFileEditorManagerImpl)FileEditorManager.getInstance(myProject)).createEditorComposite(editorInfo);
    TabInfo tabInfo = new TabInfo(editorContainer.getComponent())
      .setText(editorInfo.getFile().getPresentableName())
      .setIcon(getFileTypeIcon(editorInfo));

    tabInfo.setObject(new TabEditorData(editorInfo, editorContainer));

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTabAction(editorInfo));

    tabInfo.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    addTabSilently(tabInfo, index);
    select(tabInfo, true);
    asyncUpdateTab(tabInfo);
    myEditorManager.fireEditorSelected(editorInfo);
  }

  @Override
  public void close() {
    ObjectUtils.consumeIfNotNull(getSelectedInfo(), tabInfo -> closeTab(tabInfo));
  }

  private static Icon getFileTypeIcon(@NotNull LightEditorInfo editorInfo) {
    return editorInfo.getFile().getFileType().getIcon();
  }

  private void onSelectionChange(@Nullable TabInfo tabInfo) {
    LightEditorInfo selectedEditorInfo = tabInfo != null ? getEditorInfo(tabInfo) : null;
    myEditorManager.fireEditorSelected(selectedEditorInfo);
  }

  void selectTab(@NotNull LightEditorInfo info) {
    getTabs().stream()
      .filter(tabInfo -> info.equals(getEditorInfo(tabInfo)))
      .findFirst().ifPresent(tabInfo -> select(tabInfo, true));
  }

  private final class CloseTabAction extends DumbAwareAction implements LightEditCompatible {
    private final LightEditorInfo myEditorInfo;

    private final Icon myUnsavedIcon = LightEditSaveStatusIcon.create();

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
      e.getPresentation().setText(IdeBundle.messagePointer("action.presentation.LightEditTabs.text"));
    }

    private Icon getIcon() {
      return myEditorInfo.isSaveRequired() ? myUnsavedIcon : AllIcons.Actions.Close;
    }

    private void closeCurrentTab() {
      TabInfo tabInfo = findTabInfo(myEditorInfo);
      if (tabInfo != null) {
        closeTab(tabInfo);
      }
    }

    private void closeAllTabsExceptCurrent() {
      getTabs().stream()
        .filter(tabInfo -> tabInfo != getSelectedInfo())
        .forEach(tabInfo -> closeTab(tabInfo));
    }

  }

  private void closeTab(@NotNull TabInfo tabInfo) {
    final LightEditorInfo editorInfo = getEditorInfo(tabInfo);
    if (editorInfo != null) {
      if (!editorInfo.isSaveRequired() ||
          autosaveDocument(editorInfo) ||
          LightEditUtil.confirmClose(
            ApplicationBundle.message("light.edit.close.message"),
            ApplicationBundle.message("light.edit.close.title"),
            new LightEditSaveConfirmationHandler() {
              @Override
              public void onSave() {
                saveDocument(editorInfo);
              }

              @Override
              public void onDiscard() {
                FileEditor fileEditor = editorInfo.getFileEditor();
                if (fileEditor instanceof TextEditor) {
                  Editor editor = ((TextEditor)fileEditor).getEditor();
                  FileDocumentManager.getInstance().reloadFromDisk(editor.getDocument());
                }
              }
            })
      ) {
        removeTab(tabInfo).doWhenDone(() -> myEditorManager.closeEditor(editorInfo));
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

  void closeTab(@NotNull LightEditorInfo editorInfo) {
    TabInfo tabInfo = findTabInfo(editorInfo);
    if (tabInfo != null) {
      closeTab(tabInfo);
    }
  }

  void saveDocument(@NotNull LightEditorInfo editorInfo) {
    if (editorInfo.isNew()) {
      VirtualFile targetFile = LightEditUtil.getPreferredSaveTarget(editorInfo);
      if (targetFile == null) {
        targetFile = LightEditUtil.chooseTargetFile(this.getParent(), editorInfo);
      }
      if (targetFile != null) {
        myEditorManager.saveAs(editorInfo, targetFile);
      }
    }
    else {
      Document document = FileDocumentManager.getInstance().getDocument(editorInfo.getFile());
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }
    }
  }

  void replaceTab(@NotNull LightEditorInfo oldInfo, @NotNull LightEditorInfo newInfo) {
    TabInfo oldTabInfo = findTabInfo(oldInfo);
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
      LightEditorInfo editorInfo = getEditorInfo(info);
      if (editorInfo != null) {
        return editorInfo.getFile();
      }
    }
    return null;
  }

  List<VirtualFile> getOpenFiles() {
    return getTabs().stream()
                    .map(tab -> getEditorInfo(tab))
                    .filter(editorInfo -> editorInfo != null)
                    .map(editorInfo -> editorInfo.getFile())
                    .collect(Collectors.toList());
  }

  @Nullable
  FileEditor getSelectedFileEditor() {
    TabInfo info = getSelectedInfo();
    if (info != null) {
      LightEditorInfo editorInfo = getEditorInfo(info);
      if (editorInfo != null) {
        return editorInfo.getFileEditor();
      }
    }
    return null;
  }

  private static class TabEditorData {
    private final @NotNull LightEditorInfo editorInfo;
    private final @NotNull EditorComposite editorComposite;

    private TabEditorData(@NotNull LightEditorInfo editorInfo, @NotNull EditorComposite editorComposite) {
      this.editorInfo = editorInfo;
      this.editorComposite = editorComposite;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TabEditorData && ((TabEditorData)obj).editorInfo.equals(editorInfo);
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return getSelectedFile();
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      final VirtualFile selectedFile = getSelectedFile();
      return selectedFile != null ? new VirtualFile[] {selectedFile} : VirtualFile.EMPTY_ARRAY;
    }
    else if (CloseAction.CloseTarget.KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  private void asyncUpdateTab(@NotNull TabInfo tabInfo) {
    assert ApplicationManager.getApplication().isDispatchThread();
    LightEditorInfo editorInfo = getEditorInfo(tabInfo);
    if (editorInfo == null) return;
    EditorNotifications.getInstance(myProject).updateNotifications(editorInfo.getFile());
    asyncUpdateTabs(Collections.singletonList(Pair.createNonNull(tabInfo, editorInfo)));
  }

  private void asyncUpdateTabs(@NotNull List<? extends Pair.NonNull<TabInfo, LightEditorInfo>> tabEditorPairs) {
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
        TabInfo info = findTabInfo(editorInfo);
        if (info == null) return null;
        return Pair.createNonNull(info, editorInfo);
      });
      if (!tabEditorPairs.isEmpty()) {
        asyncUpdateTabs(tabEditorPairs);
      }
    });
  }

  @Nullable
  private static LightEditorInfo getEditorInfo(@NotNull TabInfo tabInfo) {
    Object data = tabInfo.getObject();
    return data instanceof TabEditorData ? ((TabEditorData)data).editorInfo : null;
  }

  @Nullable
  private TabInfo findTabInfo(@NotNull LightEditorInfo editorInfo) {
    for (TabInfo tabInfo : getTabs()) {
      final Object data = tabInfo.getObject();
      if (data instanceof TabEditorData && ((TabEditorData)data).editorInfo.equals(editorInfo)) {
        return tabInfo;
      }
    }
    return null;
  }

  @Nullable
  public EditorComposite findEditorComposite(@NotNull FileEditor fileEditor) {
    VirtualFile virtualFile = fileEditor.getFile();
    if (virtualFile != null) {
      for (TabInfo tabInfo : getTabs()) {
        final Object data = tabInfo.getObject();
        if (data instanceof TabEditorData && virtualFile.equals(((TabEditorData)data).editorInfo.getFile())) {
          EditorComposite composite = ((TabEditorData)data).editorComposite;
          if (ContainerUtil.exists(composite.getAllEditors(), editor->editor.equals(fileEditor))) {
            return composite;
          }
        }
      }
    }
    return null;
  }

  boolean isTabNavigationAvailable(@NotNull AnAction navigationAction) {
    if (getTabCount() > 1) {
      Component focusOwner = FocusManager.getCurrentManager().getFocusOwner();
      if (focusOwner instanceof EditorComponentImpl) {
        return getIndexOf(getSelectedInfo()) >= 0 &&
               (navigationAction instanceof PreviousTabAction || navigationAction instanceof NextTabAction);
      }
    }
    return false;
  }

  void navigateToTab(@NotNull AnAction navigationAction) {
    int currIndex = getIndexOf(getSelectedInfo());
    if (currIndex >= 0) {
      int newIndex = currIndex;
      if (navigationAction instanceof PreviousTabAction) {
        newIndex = (currIndex > 0 ? currIndex : getTabCount()) - 1;
      }
      else if (navigationAction instanceof NextTabAction) {
        newIndex = currIndex < getTabCount() - 1 ? currIndex + 1 : 0;
      }
      if (newIndex != currIndex) {
        TabInfo newInfo = getTabAt(newIndex);
        select(newInfo, true);
      }
    }
  }
}

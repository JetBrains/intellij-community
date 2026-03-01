// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.structureView.newStructureView.StructurePopup;
import com.intellij.ide.structureView.newStructureView.StructurePopupProvider;
import com.intellij.ide.structureView.newStructureView.StructurePopupTestExt;
import com.intellij.ide.util.StructureViewCompositeModel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public final class ViewStructureAction extends DumbAwareAction {

  @Nullable
  private final Consumer<AbstractTreeNode<?>> myCallbackAfterNavigation;

  public ViewStructureAction() {
    setEnabledInModalContext(true);
    myCallbackAfterNavigation = null;
  }

  @ApiStatus.Internal
  public ViewStructureAction(@Nullable Consumer<AbstractTreeNode<?>> callbackAfterNavigation) {
    myCallbackAfterNavigation = callbackAfterNavigation;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    if (fileEditor == null) return;

    VirtualFile virtualFile = fileEditor.getFile();
    Editor editor = fileEditor instanceof TextEditor te ? te.getEditor() :
                    e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }

    StructurePopup popup = createPopup(project, fileEditor, myCallbackAfterNavigation);
    if (popup == null) return;

    String title = virtualFile == null ? fileEditor.getName() : virtualFile.getName();
    popup.setTitle(title);
    popup.show();
  }

  public static @Nullable StructurePopup createPopup(@NotNull Project project, @NotNull FileEditor fileEditor) {
    return createPopup(project, fileEditor, null);
  }

  /**
   * callbackAfterNavigation doesn't work in the new file structure popup
   */
  @ApiStatus.Internal
  public static @Nullable StructurePopup createPopup(@NotNull Project project,
                                                     @NotNull FileEditor fileEditor,
                                                     @Nullable Consumer<AbstractTreeNode<?>> callbackAfterNavigation) {
    return StructurePopupProvider.EP.getExtensionList().stream()
      .map(provider -> provider.createPopup(project, fileEditor, callbackAfterNavigation))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    Editor editor = fileEditor instanceof TextEditor te ? te.getEditor() :
                    e.getData(CommonDataKeys.EDITOR);

    boolean enabled = fileEditor != null &&
                      (!Boolean.TRUE.equals(EditorTextField.SUPPLEMENTARY_KEY.get(editor))) &&
                      (Registry.is("frontend.structure.popup") || fileEditor.getStructureViewBuilder() != null);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static @NotNull StructureViewModel createStructureViewModel(@NotNull Project project,
                                                                     @NotNull FileEditor fileEditor,
                                                                     @NotNull StructureView structureView) {
    StructureViewModel treeModel;
    VirtualFile virtualFile = fileEditor.getFile();
    if (structureView instanceof StructureViewComposite && virtualFile != null) {
      StructureViewComposite.StructureViewDescriptor[] views = ((StructureViewComposite)structureView).getStructureViews();
      PsiFile psiFile = Objects.requireNonNull(PsiManager.getInstance(project).findFile(virtualFile));
      treeModel = new StructureViewCompositeModel(psiFile, EditorUtil.getEditorEx(fileEditor), Arrays.asList(views));
      Disposer.register(structureView, treeModel);
    }
    else {
      treeModel = structureView.getTreeModel();
    }
    return treeModel;
  }

  @ApiStatus.Internal
  @TestOnly
  public static @Nullable StructurePopupTestExt createPopupForTest(@NotNull Project project, @NotNull FileEditor fileEditor) {
    StructurePopup popup = createPopup(project, fileEditor, null);
    return popup instanceof StructurePopupTestExt ? (StructurePopupTestExt)popup : null;
  }
}
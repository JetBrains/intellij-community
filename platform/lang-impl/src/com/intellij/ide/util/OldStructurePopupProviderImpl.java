// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.logical.PhysicalAndLogicalStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructurePopupProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.idea.AppMode;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.intellij.ide.actions.ViewStructureAction.createStructureViewModel;

class OldStructurePopupProviderImpl implements StructurePopupProvider {
  @Override
  public @Nullable FileStructurePopup createPopup(@NotNull Project project,
                                                  @NotNull FileEditor fileEditor,
                                                  @Nullable Consumer<AbstractTreeNode<?>> callbackAfterNavigation) {
    if (!AppMode.isRemoteDevHost() && Registry.is("frontend.structure.popup")) return null;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    StructureViewBuilder builder = fileEditor.getStructureViewBuilder();
    if (builder == null) return null;
    project.getMessageBus().syncPublisher(FileStructurePopupListener.TOPIC).stateChanged(true);
    StructureView structureView;
    StructureViewModel treeModel;
    if (builder instanceof PhysicalAndLogicalStructureViewBuilder compositeBuilder) {
      structureView = compositeBuilder.createPhysicalStructureView(fileEditor, project);
      treeModel = createStructureViewModel(project, fileEditor, structureView);
    }
    else if (builder instanceof TreeBasedStructureViewBuilder) {
      structureView = null;
      treeModel = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel(EditorUtil.getEditorEx(fileEditor));
    }
    else {
      structureView = builder.createStructureView(fileEditor, project);
      treeModel = createStructureViewModel(project, fileEditor, structureView);
    }
    if (treeModel instanceof PlaceHolder) {
      ((PlaceHolder)treeModel).setPlace(TreeStructureUtil.PLACE);
    }
    FileStructurePopup popup = new FileStructurePopup(project, fileEditor, treeModel, callbackAfterNavigation);
    if (structureView != null) Disposer.register(popup, structureView);
    return popup;
  }
}

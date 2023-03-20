// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.RenameablePackagingElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

public class RenamePackagingElementAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public RenamePackagingElementAction(ArtifactEditorEx artifactEditor) {
    super(JavaUiBundle.message("action.name.rename.packaging.element"));
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactEditor.getLayoutTreeComponent().getTreePanel());
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    final boolean visible = element instanceof RenameablePackagingElement && ((RenameablePackagingElement)element).canBeRenamed();
    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    final PackagingElement<?> element = selection.getElementIfSingle();
    if (node == null || element == null) return;
    if (!myArtifactEditor.getLayoutTreeComponent().checkCanModify(element, node)) return;
    
    final TreePath path = selection.getPath(node);
    myArtifactEditor.getLayoutTreeComponent().startRenaming(path);
  }
}

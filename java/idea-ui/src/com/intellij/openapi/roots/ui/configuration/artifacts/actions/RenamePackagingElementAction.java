/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.RenameablePackagingElement;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public class RenamePackagingElementAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public RenamePackagingElementAction(ArtifactEditorEx artifactEditor) {
    super(ProjectBundle.message("action.name.rename.packaging.element"));
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactEditor.getLayoutTreeComponent().getTreePanel());
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    final boolean visible = element instanceof RenameablePackagingElement && ((RenameablePackagingElement)element).canBeRenamed();
    e.getPresentation().setEnabled(visible);
    e.getPresentation().setVisible(visible);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    final PackagingElement<?> element = selection.getElementIfSingle();
    if (node == null || element == null) return;
    if (!myArtifactEditor.getLayoutTreeComponent().checkCanModify(element, node)) return;
    
    final TreePath path = selection.getPath(node);
    myArtifactEditor.getLayoutTreeComponent().startRenaming(path);
  }
}

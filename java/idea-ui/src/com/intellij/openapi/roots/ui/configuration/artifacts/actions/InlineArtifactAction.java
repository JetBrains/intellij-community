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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;

import java.util.Collections;

/**
 * @author nik
 */
public class InlineArtifactAction extends DumbAwareAction {
  private final ArtifactEditorEx myEditor;

  public InlineArtifactAction(ArtifactEditorEx editor) {
    super(ProjectBundle.message("action.name.inline.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    PackagingElement<?> element = selection.getElementIfSingle();
    e.getPresentation().setEnabled(element instanceof ArtifactPackagingElement && node != null && node.getParentElement(element) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getLayoutTreeComponent();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null || !(element instanceof ArtifactPackagingElement)) return;

    final CompositePackagingElement<?> parent = node.getParentElement(element);
    final CompositePackagingElementNode parentNode = node.getParentNode();
    if (parent == null || parentNode == null) {
      return;
    }
    if (!treeComponent.checkCanModifyChildren(parent, parentNode, Collections.singletonList(node))) return;

    treeComponent.editLayout(new Runnable() {
      @Override
      public void run() {
        parent.removeChild(element);
        final ArtifactEditorContext context = myEditor.getContext();
        final Artifact artifact = ((ArtifactPackagingElement)element).findArtifact(context);
        if (artifact != null) {
          final CompositePackagingElement<?> rootElement = artifact.getRootElement();
          if (rootElement instanceof ArtifactRootElement<?>) {
            for (PackagingElement<?> child : rootElement.getChildren()) {
              parent.addOrFindChild(ArtifactUtil.copyWithChildren(child, context.getProject()));
            }
          }
          else {
            parent.addOrFindChild(ArtifactUtil.copyWithChildren(rootElement, context.getProject()));
          }
        }
      }
    });
    treeComponent.rebuildTree();
  }
}

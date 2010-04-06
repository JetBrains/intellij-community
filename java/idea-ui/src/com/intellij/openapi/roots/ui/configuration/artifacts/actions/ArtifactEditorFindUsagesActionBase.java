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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ArtifactsTreeNode;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author nik
 */
public abstract class ArtifactEditorFindUsagesActionBase extends FindUsagesInProjectStructureActionBase {
  private final Tree myTree;
  private final ArtifactsStructureConfigurableContext myArtifactContext;

  public ArtifactEditorFindUsagesActionBase(Tree tree, Project project, ArtifactsStructureConfigurableContext artifactContext) {
    super(tree, project);
    myTree = tree;
    myArtifactContext = artifactContext;
  }

  protected boolean isEnabled() {
    ArtifactsTreeNode node = getSelectedNode();
    return node != null && node.getElementPresentation().getSourceObject() != null;
  }

  protected ProjectStructureElement getSelectedElement() {
    ArtifactsTreeNode node = getSelectedNode();
    if (node == null) {
      return null;
    }

    final Object sourceObject = node.getElementPresentation().getSourceObject();
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(getContext().getProject()).getContext();
    if (sourceObject instanceof Module) {
      return new ModuleProjectStructureElement(context, (Module)sourceObject);
    }
    else if (sourceObject instanceof Library) {
      return new LibraryProjectStructureElement(context, (Library)sourceObject);
    }
    else if (sourceObject instanceof Artifact) {
      return myArtifactContext.getOrCreateArtifactElement((Artifact)sourceObject);
    }
    return null;
  }

  @Nullable
  protected abstract ArtifactsTreeNode getSelectedNode();

  protected RelativePoint getPointToShowResults() {
    final int selectedRow = myTree.getSelectionRows()[0];
    final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
    final Point location = rowBounds.getLocation();
    location.y += rowBounds.height;
    return new RelativePoint(myTree, location);
  }
}

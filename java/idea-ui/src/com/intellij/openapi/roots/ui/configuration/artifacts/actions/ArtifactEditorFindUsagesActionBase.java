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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;

import java.awt.*;

/**
 * @author nik
 */
public abstract class ArtifactEditorFindUsagesActionBase extends FindUsagesInProjectStructureActionBase {
  private final Tree myTree;
  protected final ArtifactsStructureConfigurableContext myArtifactContext;

  public ArtifactEditorFindUsagesActionBase(Tree tree, Project project, ArtifactsStructureConfigurableContext artifactContext) {
    super(tree, project);
    myTree = tree;
    myArtifactContext = artifactContext;
  }

  @Override
  protected boolean isEnabled() {
    return getSelectedElement() != null;
  }

  @Override
  protected RelativePoint getPointToShowResults() {
    final int selectedRow = myTree.getSelectionRows()[0];
    final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
    final Point location = rowBounds.getLocation();
    location.y += rowBounds.height;
    return new RelativePoint(myTree, location);
  }
}

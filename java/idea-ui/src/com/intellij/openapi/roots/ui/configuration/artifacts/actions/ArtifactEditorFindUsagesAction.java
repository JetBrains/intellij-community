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

import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;

import java.awt.*;

/**
* @author nik
*/
public class ArtifactEditorFindUsagesAction extends FindUsagesInProjectStructureActionBase {
  private LayoutTreeComponent myLayoutTreeComponent;

  public ArtifactEditorFindUsagesAction(LayoutTreeComponent layoutTreeComponent, Project project) {
    super(layoutTreeComponent.getLayoutTree(), project);
    myLayoutTreeComponent = layoutTreeComponent;
  }

  protected boolean isEnabled() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null && node.getElementPresentation().getSourceObject() != null;
  }

  protected Object getSelectedObject() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null ? node.getElementPresentation().getSourceObject() : null;
  }

  protected RelativePoint getPointToShowResults() {
    final int selectedRow = myLayoutTreeComponent.getLayoutTree().getSelectionRows()[0];
    final Rectangle rowBounds = myLayoutTreeComponent.getLayoutTree().getRowBounds(selectedRow);
    final Point location = rowBounds.getLocation();
    location.y += rowBounds.height;
    return new RelativePoint(myLayoutTreeComponent.getLayoutTree(), location);
  }
}

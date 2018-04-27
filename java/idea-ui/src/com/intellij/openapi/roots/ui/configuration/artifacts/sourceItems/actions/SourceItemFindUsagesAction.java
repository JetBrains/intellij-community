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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.ArtifactEditorFindUsagesActionBase;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.*;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.packaging.ui.PackagingSourceItem;

import java.util.List;

/**
 * @author nik
 */
public class SourceItemFindUsagesAction extends ArtifactEditorFindUsagesActionBase {
  private final SourceItemsTree myTree;

  public SourceItemFindUsagesAction(SourceItemsTree tree, Project project, ArtifactsStructureConfigurableContext artifactContext) {
    super(tree, project, artifactContext);
    myTree = tree;
  }

  @Override
  protected ProjectStructureElement getSelectedElement() {
    final List<SourceItemNode> nodes = myTree.getSelectedSourceItemNodes();
    if (nodes.size() != 1) return null;
    SourceItemNode node = nodes.get(0);
    if (node == null) {
      return null;
    }

    PackagingSourceItem sourceItem = node.getSourceItem();
    if (sourceItem == null) return null;

    final StructureConfigurableContext context = getContext();
    if (sourceItem instanceof ModuleOutputSourceItem) {
      return new ModuleProjectStructureElement(context, ((ModuleOutputSourceItem)sourceItem).getModule());
    }
    else if (sourceItem instanceof LibrarySourceItem) {
      return new LibraryProjectStructureElement(context, ((LibrarySourceItem)sourceItem).getLibrary());
    }
    else if (sourceItem instanceof ArtifactSourceItem) {
      return myArtifactContext.getOrCreateArtifactElement(((ArtifactSourceItem)sourceItem).getArtifact());
    }
    return null;
  }
}

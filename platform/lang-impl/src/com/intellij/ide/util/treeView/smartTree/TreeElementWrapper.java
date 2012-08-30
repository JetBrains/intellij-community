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

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.project.Project;

import java.util.Collection;

public class TreeElementWrapper extends CachingChildrenTreeNode<TreeElement> {
  public TreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
    super(project, value, treeModel);
  }

  @Override
  public void copyFromNewInstance(final CachingChildrenTreeNode oldInstance) {
  }

  @Override
  public void update(PresentationData presentation) {
    if (((StructureViewTreeElement)getValue()).getValue() != null) {
      presentation.updateFrom(getValue().getPresentation());
    }
  }

  @Override
  public void initChildren() {
    clearChildren();
    TreeElement[] children = getValue().getChildren();
    for (TreeElement child : children) {
      addSubElement(createChildNode(child));
    }
    if (myTreeModel instanceof ProvidingTreeModel) {
      final Collection<NodeProvider> providers = ((ProvidingTreeModel)myTreeModel).getNodeProviders();
      for (NodeProvider provider : providers) {
        if (((ProvidingTreeModel)myTreeModel).isEnabled(provider)) {
          final Collection<TreeElement> nodes = provider.provideNodes(getValue());
          for (TreeElement node : nodes) {
            addSubElement(createChildNode(node));
          }
        }
      }
    }
  }

  @Override
  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}

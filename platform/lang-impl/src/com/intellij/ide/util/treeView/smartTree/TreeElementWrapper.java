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

public class TreeElementWrapper extends CachingChildrenTreeNode<TreeElement>{
  public TreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
    super(project, value, treeModel);
  }

  public void copyFromNewInstance(final CachingChildrenTreeNode oldInstance) {
  }

  public void update(PresentationData presentation) {
    if (((StructureViewTreeElement)getValue()).getValue() != null){
      presentation.updateFrom(getValue().getPresentation());
    }
  }
  public void initChildren() {
    clearChildren();
    TreeElement[] children = getValue().getChildren();
    for (TreeElement child : children) {
      addSubElement(createChildNode(child));
    }
  }

  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}

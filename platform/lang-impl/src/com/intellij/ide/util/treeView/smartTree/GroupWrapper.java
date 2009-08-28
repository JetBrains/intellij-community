package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;

import java.util.Collection;

class GroupWrapper extends CachingChildrenTreeNode<Group> {
  public GroupWrapper(Project project, Group value, TreeModel treeModel) {
    super(project, value, treeModel);
    clearChildren();
  }

  public void copyFromNewInstance(final CachingChildrenTreeNode newInstance) {
    clearChildren();
    setChildren(newInstance.getChildren());
    synchronizeChildren();
  }

  public void update(PresentationData presentation) {
    presentation.updateFrom(getValue().getPresentation());
  }

  public void initChildren() {
    clearChildren();
    Collection<TreeElement> children = getValue().getChildren();
    for (TreeElement child : children) {
      TreeElementWrapper childNode = new TreeElementWrapper(getProject(), child, myTreeModel);
      addSubElement(childNode);
    }
  }


  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}

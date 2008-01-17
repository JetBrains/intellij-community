package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 22, 2005
 */
public class ProjectViewModuleGroupNode extends ModuleGroupNode {
  public ProjectViewModuleGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    super(project, (ModuleGroup)value, viewSettings);
  }

  public ProjectViewModuleGroupNode(final Project project, final ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected Class<? extends AbstractTreeNode> getModuleNodeClass() {
    return ProjectViewModuleNode.class;
  }

  protected ModuleGroupNode createModuleGroupNode(ModuleGroup moduleGroup) {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }


}

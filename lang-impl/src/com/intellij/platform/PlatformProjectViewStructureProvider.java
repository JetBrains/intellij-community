package com.intellij.platform;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;

import java.util.Collection;

/**
 * @author yole
 */
public class PlatformProjectViewStructureProvider implements TreeStructureProvider {
  public Collection<AbstractTreeNode> modify(final AbstractTreeNode parent, final Collection<AbstractTreeNode> children, final ViewSettings settings) {
    if (parent instanceof ProjectViewProjectNode) {
      for(AbstractTreeNode child: children) {
        if (child instanceof ProjectViewModuleNode) {
          return child.getChildren();
        }
      }
    }
    return children;
  }

  public Object getData(final Collection<AbstractTreeNode> selected, final String dataName) {
    return null;
  }
}

package ru.compscicenter.edide;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * author: liana
 * data: 6/25/14.
 */
public class StudyTreeStructureProvider implements TreeStructureProvider{
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    Collection<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    for (AbstractTreeNode node:children) {
      if (node.getValue() instanceof PsiDirectory) {
        StudyDirectoryNode newNode = new StudyDirectoryNode(node.getProject(), (PsiDirectory)node.getValue(), settings);
        nodes.add(newNode);
      }  else {
        if (parent instanceof StudyDirectoryNode) {
          nodes.add(node);
        }
      }
    }
    return nodes;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}

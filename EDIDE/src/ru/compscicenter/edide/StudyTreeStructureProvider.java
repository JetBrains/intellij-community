package ru.compscicenter.edide;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.TaskFile;

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
    if (!needModify(parent)) {
      return  children;
    }
    Collection<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    for (AbstractTreeNode node:children) {
      Project project = node.getProject();
      if (node.getValue() instanceof PsiDirectory) {
        PsiDirectory nodeValue = (PsiDirectory)node.getValue();
        StudyDirectoryNode newNode = new StudyDirectoryNode(project, nodeValue, settings);
        nodes.add(newNode);
      }  else {
        if (parent instanceof StudyDirectoryNode) {
          if (node instanceof PsiFileNode) {
            PsiFileNode psiFileNode = (PsiFileNode) node;
            TaskFile taskFile = StudyTaskManager.getInstance(project).getTaskFile(psiFileNode.getVirtualFile());
            if (taskFile != null) {
              nodes.add(node);
            }
          }
        }
      }
    }
    return nodes;
  }

  private boolean needModify(AbstractTreeNode parent) {
    Project project = parent.getProject();
    if (project != null) {
      StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      if (studyTaskManager.getCourse() == null) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}

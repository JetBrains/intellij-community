package com.intellij.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class ClassNameConvertor implements TreeStructureProvider {

  private final Project myProject;

  public ClassNameConvertor(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      Object o = treeNode.getValue();
      if (o instanceof PsiFile && ((PsiFile)o).getVirtualFile().getExtension().equals("java")) {
        final String name = ((PsiFile)o).getName();
        ProjectViewNode viewNode = new ProjectViewNode<PsiFile>(myProject, (PsiFile)o, settings) {
          @NotNull
          public Collection<AbstractTreeNode> getChildren() {
            return Collections.emptyList();
          }

          public String getTestPresentation() {
            return name + " converted";
          }

          public boolean contains(@NotNull VirtualFile file) {
            return false;
          }

          public void update(PresentationData presentation) {
          }

        };
        result.add(viewNode);
      }
      else {
        result.add(treeNode);
      }
    }
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}

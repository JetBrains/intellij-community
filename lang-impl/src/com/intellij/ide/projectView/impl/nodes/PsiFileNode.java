package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Collection;

public class PsiFileNode extends BasePsiNode<PsiFile>{

  public PsiFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    /*if (getSettings().isStructureView() && getValue() instanceof PsiJavaFile){
      PsiClass[] classes = ((PsiJavaFile)getValue()).getClasses();
      ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PsiClass aClass : classes) {
        result.add(new ClassTreeNode(getProject(), aClass, getSettings()));
      }
      return result;
    } else {
      return new ArrayList<AbstractTreeNode>();
    }*/
    return new ArrayList<AbstractTreeNode>();
  }

  protected void updateImpl(PresentationData data) {
    final PsiFile value = getValue();
    data.setPresentableText(value.getName());
    data.setIcons(value.getIcon(Iconable.ICON_FLAG_READ_STATUS));
  }

  public VirtualFile getVirtualFile() {
    return getValue().getVirtualFile();
  }

  public int getWeight() {
    return 20;
  }

  @Override
  public String getTitle() {
    final PsiFile file = getValue();
    if (file != null) {
      return file.getVirtualFile().getPresentableUrl();
    }
    return super.getTitle();
  }

  @Override
  protected boolean isMarkReadOnly() {
    return true;
  }
}

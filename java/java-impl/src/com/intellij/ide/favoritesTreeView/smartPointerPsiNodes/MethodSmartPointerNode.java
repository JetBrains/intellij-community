package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class MethodSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{
  public MethodSmartPointerNode(Project project, PsiMethod value, ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createLazyPointer(value), viewSettings);
  }

  public MethodSmartPointerNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (PsiMethod)value, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildrenImpl() {
    return Collections.emptyList();
  }

  public void updateImpl(PresentationData data) {
    String name = PsiFormatUtil.formatMethod(
      (PsiMethod)getPsiElement(),
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }

  public boolean isConstructor() {
    final PsiMethod psiMethod = (PsiMethod)getPsiElement();
    return psiMethod != null && psiMethod.isConstructor();
  }

}

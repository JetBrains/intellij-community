// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClassSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{

  private boolean isAlwaysExpand;

  public ClassSmartPointerNode(@NotNull Project project, @NotNull PsiClass value, @NotNull ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(value), viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    PsiClass parent = getPsiClass();
    List<AbstractTreeNode<?>> treeNodes = new ArrayList<>();

    if (getSettings().isShowMembers()) {
      List<PsiElement> result = new ArrayList<>();
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (PsiElement psiElement : result) {
        psiElement.accept(new JavaElementVisitor() {
          @Override public void visitClass(@NotNull PsiClass aClass) {
            treeNodes.add(new ClassSmartPointerNode(getProject(), aClass, getSettings()));
          }

          @Override public void visitMethod(@NotNull PsiMethod method) {
            treeNodes.add(new MethodSmartPointerNode(getProject(), method, getSettings()));
          }

          @Override public void visitField(@NotNull PsiField field) {
            treeNodes.add(new FieldSmartPointerNode(getProject(), field, getSettings()));
          }

          @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            visitExpression(expression);
          }
        });
      }
    }
    return treeNodes;
  }

  @Override
  public boolean isAlwaysLeaf() {
    return !getSettings().isShowMembers();
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    final PsiClass aClass = getPsiClass();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
    }
    isAlwaysExpand = getParentValue() instanceof PsiFile;
  }

  public boolean isTopLevel() {
    return getPsiElement() != null && getPsiElement().getParent() instanceof PsiFile;
  }


  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  public PsiClass getPsiClass() {
    return (PsiClass)getPsiElement();
  }

  @Override
  public boolean isAlwaysExpand() {
    return isAlwaysExpand;
  }
}

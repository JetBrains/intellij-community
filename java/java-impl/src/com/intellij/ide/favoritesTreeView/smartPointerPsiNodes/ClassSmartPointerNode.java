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

public class ClassSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{
  public ClassSmartPointerNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(value), viewSettings);
  }

  public ClassSmartPointerNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiClass)value, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getPsiClass();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<>();

    ArrayList<PsiElement> result = new ArrayList<>();
    if (getSettings().isShowMembers()) {
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (PsiElement psiElement : result) {
        psiElement.accept(new JavaElementVisitor() {
          @Override public void visitClass(PsiClass aClass) {
            treeNodes.add(new ClassSmartPointerNode(getProject(), aClass, getSettings()));
          }

          @Override public void visitMethod(PsiMethod method) {
            treeNodes.add(new MethodSmartPointerNode(getProject(), method, getSettings()));
          }

          @Override public void visitField(PsiField field) {
            treeNodes.add(new FieldSmartPointerNode(getProject(), field, getSettings()));
          }

          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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
  public void updateImpl(PresentationData data) {
    final PsiClass aClass = getPsiClass();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
    }
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
    return getParentValue() instanceof PsiFile;
  }
}

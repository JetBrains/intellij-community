// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaMethodHierarchyProvider implements HierarchyProvider {
  @Override
  public PsiElement getTarget(@NotNull DataContext dataContext) {
    PsiMethod method = getMethodImpl(dataContext);
    if (
      method != null &&
      method.getContainingClass() != null &&
      !method.hasModifierProperty(PsiModifier.PRIVATE) &&
      !method.hasModifierProperty(PsiModifier.STATIC)
    ){
      return method;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static PsiMethod getMethodImpl(DataContext dataContext){
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);

    if (method != null) {
      return method;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return null;
    }

    int offset = editor.getCaretModel().getOffset();
    if (offset < 1) {
      return null;
    }

    element = psiFile.findElementAt(offset);
    if (!(element instanceof PsiWhiteSpace)) {
      return null;
    }

    element = psiFile.findElementAt(offset - 1);
    if (!(element instanceof PsiJavaToken) || ((PsiJavaToken)element).getTokenType() != JavaTokenType.SEMICOLON) {
      return null;
    }

    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }

  @Override
  @NotNull
  public HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
    return new MethodHierarchyBrowser(target.getProject(), (PsiMethod) target);
  }

  @Override
  public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
    ((MethodHierarchyBrowser) hierarchyBrowser).changeView(MethodHierarchyBrowserBase.getMethodType());
  }
}

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
package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UnimplementInterfaceAction implements IntentionAction {
  private String myName = "Interface";

  @Override
  @NotNull
  public String getText() {
    return "Unimplement " + myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Unimplement Interface/Class";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    if (psiReference == null) return false;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return false;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    PsiJavaCodeReferenceElement referenceElement = getTopLevelRef(psiReference, referenceList);
    if (referenceElement == null) return false;

    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass)) return false;

    PsiClass targetClass = (PsiClass)target;
    if (targetClass.isInterface()) {
      myName = "Interface";
    }
    else {
      myName = "Class";
    }

    return true;
  }

  @Nullable
  private static PsiJavaCodeReferenceElement getTopLevelRef(PsiReference psiReference, PsiReferenceList referenceList) {
    PsiElement element = psiReference.getElement();
    while (element.getParent() != referenceList) {
      element = element.getParent();
      if (element == null) return null;
    }

    if (!(element instanceof PsiJavaCodeReferenceElement)) return null;
    return (PsiJavaCodeReferenceElement)element;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    if (psiReference == null) return;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return;

    PsiJavaCodeReferenceElement element = getTopLevelRef(psiReference, referenceList);
    if (element == null) return;

    final PsiElement target = element.resolve();
    if (!(target instanceof PsiClass)) return;

    PsiClass targetClass = (PsiClass)target;

    final Map<PsiMethod, PsiMethod> implementations = new HashMap<>();
    for (PsiMethod psiMethod : targetClass.getAllMethods()) {
      final PsiMethod implementingMethod = MethodSignatureUtil.findMethodBySuperMethod(psiClass, psiMethod, false);
      if (implementingMethod != null) {
        implementations.put(psiMethod, implementingMethod);
      }
    }
    element.delete();

    if (target == psiClass) return;

    final Set<PsiMethod> superMethods = new HashSet<>();
    for (PsiClass aClass : psiClass.getSupers()) {
      Collections.addAll(superMethods, aClass.getAllMethods());
    }
    final PsiMethod[] psiMethods = targetClass.getAllMethods();
    for (PsiMethod psiMethod : psiMethods) {
      if (superMethods.contains(psiMethod)) continue;
      final PsiMethod impl = implementations.get(psiMethod);
      if (impl != null) impl.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

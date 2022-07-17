// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UnimplementInterfaceAction extends BaseElementAtCaretIntentionAction {
  private String myName = "Interface";
  private final String myClassName;
  private final boolean myIsDuplicates;

  public UnimplementInterfaceAction() {
    this(null, false);
  }

  public UnimplementInterfaceAction(String className, boolean isDuplicates) {
    myClassName = className;
    myIsDuplicates = isDuplicates;
  }

  @Override
  @NotNull
  public String getText() {
    if (myIsDuplicates) {
      return JavaBundle.message("intention.text.implements.list.remove.others", myClassName);
    }
    return JavaBundle.message("intention.text.unimplement.0", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.unimplement.interface.class");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList == null) return false;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    final PsiJavaCodeReferenceElement topLevelRef = getTopLevelRef(element, referenceList);
    if (topLevelRef == null) return false;

    final PsiClass targetClass = ObjectUtils.tryCast(topLevelRef.resolve(), PsiClass.class);
    if (targetClass == null) return false;

    if (myIsDuplicates) return true;

    for (PsiJavaCodeReferenceElement refElement : referenceList.getReferenceElements()) {
      if (isDuplicate(topLevelRef, refElement, targetClass)) return false;
    }

    myName = targetClass.isInterface() ? "Interface" : "Class";

    return true;
  }

  @Nullable
  private static PsiJavaCodeReferenceElement getTopLevelRef(@NotNull PsiElement element, @NotNull PsiReferenceList referenceList) {
    while (element.getParent() != referenceList) {
      element = element.getParent();
      if (element == null) return null;
    }
    return ObjectUtils.tryCast(element, PsiJavaCodeReferenceElement.class);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList == null) return;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return;

    final PsiJavaCodeReferenceElement topLevelRef = getTopLevelRef(element, referenceList);
    if (topLevelRef == null) return;

    final PsiElement target = topLevelRef.resolve();
    final PsiClass targetClass = ObjectUtils.tryCast(target, PsiClass.class);
    if (targetClass == null) return;

    if (myIsDuplicates) {
      for (PsiJavaCodeReferenceElement refElement : referenceList.getReferenceElements()) {
        if (isDuplicate(topLevelRef, refElement, targetClass)) {
          refElement.delete();
        }
      }
      return;
    }

    final Map<PsiMethod, PsiMethod> implementations = new HashMap<>();
    for (PsiMethod psiMethod : targetClass.getAllMethods()) {
      final PsiMethod implementingMethod = MethodSignatureUtil.findMethodBySuperMethod(psiClass, psiMethod, false);
      if (implementingMethod != null) {
        implementations.put(psiMethod, implementingMethod);
      }
    }
    topLevelRef.delete();

    if (target == psiClass) return;

    if (targetClass.hasModifierProperty(PsiModifier.SEALED)) {
      SealedUtils.removeFromPermitsList(targetClass, psiClass);
      final PsiModifierList modifiers = psiClass.getModifierList();
      if (modifiers != null && modifiers.hasExplicitModifier(PsiModifier.NON_SEALED) && !SealedUtils.hasSealedParent(psiClass)) {
        modifiers.setModifierProperty(PsiModifier.NON_SEALED, false);
      }
    }

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

  private static boolean isDuplicate(PsiJavaCodeReferenceElement element,
                                     PsiJavaCodeReferenceElement otherElement,
                                     @NotNull PsiClass aClass) {
    final PsiManager manager = aClass.getManager();
    return !manager.areElementsEquivalent(otherElement, element) && manager.areElementsEquivalent(otherElement.resolve(), aClass);
  }
}

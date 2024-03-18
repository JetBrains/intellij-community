// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UnimplementInterfaceAction extends PsiUpdateModCommandAction<PsiElement> {
  final String myClassName;

  public UnimplementInterfaceAction() {
    this(null);
  }

  public UnimplementInterfaceAction(String className) {
    super(PsiElement.class);
    myClassName = className;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.unimplement.interface.class");
  }

  boolean isRemoveDuplicateExtends() {
    return false;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (shouldCorrect(element)) element = context.findLeafOnTheLeft();
    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList == null) return null;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return null;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return null;

    final PsiJavaCodeReferenceElement topLevelRef = getTopLevelRef(element, referenceList);
    if (topLevelRef == null) return null;

    final PsiClass targetClass = ObjectUtils.tryCast(topLevelRef.resolve(), PsiClass.class);
    if (targetClass == null) return null;

    if (isRemoveDuplicateExtends()) {
      return Presentation.of(getText(targetClass));
    }

    for (PsiJavaCodeReferenceElement refElement : referenceList.getReferenceElements()) {
      if (isDuplicate(topLevelRef, refElement, targetClass)) return null;
    }

    return Presentation.of(getText(targetClass));
  }

  @NotNull
  @Nls String getText(@NotNull PsiClass targetClass) {
    return JavaBundle.message("intention.text.unimplement.0", targetClass.isInterface() ? "Interface" : "Class");
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
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (shouldCorrect(element)) element = updater.getWritable(context.findLeafOnTheLeft());
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

    if (isRemoveDuplicateExtends()) {
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

  private static boolean shouldCorrect(@NotNull PsiElement element) {
    return element instanceof PsiWhiteSpace || PsiUtil.isJavaToken(element, JavaTokenType.COMMA);
  }

  private static boolean isDuplicate(PsiJavaCodeReferenceElement element,
                                     PsiJavaCodeReferenceElement otherElement,
                                     @NotNull PsiClass aClass) {
    final PsiManager manager = aClass.getManager();
    return !manager.areElementsEquivalent(otherElement, element) && manager.areElementsEquivalent(otherElement.resolve(), aClass);
  }

  public static class RemoveDuplicateExtendFix extends UnimplementInterfaceAction {
    public RemoveDuplicateExtendFix(String className) {
      super(className);
    }

    @Nls
    @Override
    @NotNull String getText(@NotNull PsiClass targetClass) {
      return JavaBundle.message("intention.text.implements.list.remove.others", myClassName);
    }

    @Override
    boolean isRemoveDuplicateExtends() {
      return true;
    }
  }
}

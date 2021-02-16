// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UnimplementInterfaceAction implements IntentionAction {
  private String myName = "Interface";
  private final PsiJavaCodeReferenceElement myRef;
  private final boolean myIsDuplicates;

  public UnimplementInterfaceAction() {
    this(null, false);
  }

  public UnimplementInterfaceAction(PsiJavaCodeReferenceElement ref, boolean isDuplicates) {
    myRef = ref;
    myIsDuplicates = isDuplicates;
  }

  @Override
  @NotNull
  public String getText() {
    if (myIsDuplicates) {
      return JavaBundle.message("intention.text.remove.duplicates");
    }
    return JavaBundle.message("intention.text.unimplement.0", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.unimplement.interface.class");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myIsDuplicates) return true;
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
    if (HighlightClassUtil.checkExtendsDuplicate(referenceElement, target, file) != null) return false;

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
    final PsiReference psiReference = myIsDuplicates ? myRef : TargetElementUtil.findReference(editor);
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

    if (myIsDuplicates) {
      final PsiManager manager = file.getManager();
      for (PsiJavaCodeReferenceElement refElement : referenceList.getReferenceElements()) {
        final PsiElement resolvedElement = refElement.resolve();
        if (!manager.areElementsEquivalent(refElement, element) && manager.areElementsEquivalent(resolvedElement, targetClass)) {
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
    element.delete();

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

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

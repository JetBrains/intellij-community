/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaDataTarget;
import com.intellij.pom.PomTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PomReferenceUtil {

  public static String getReferenceText(PomReference reference) {
    return reference.getRangeInElement().substring(reference.getElement().getText());
  }

  public static TextRange getDefaultRangeInElement(PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null: "Cannot find manipulator for " + element;
    return manipulator.getRangeInElement(element);
  }

  public static void changeContent(PomReference reference, String newContent) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(reference.getElement());
    assert manipulator != null: "Cannot find manipulator for " + reference.getElement();
    manipulator.handleContentChange(reference.getElement(), reference.getRangeInElement(), newContent);
  }


  @NotNull
  public static PomTarget convertPsi2Target(@NotNull PsiElement element) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner metaOwner = (PsiMetaOwner)element;
      final PsiMetaData psiMetaData = metaOwner.getMetaData();
      if (psiMetaData != null) {
        return new PsiMetaDataTarget(psiMetaData);
      }
    }
    if (element instanceof PomTarget) {
      return (PomTarget)element;
    }
    return new DelegatePsiTarget(element);
  }
}

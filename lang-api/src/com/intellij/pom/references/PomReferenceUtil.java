/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;

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


}

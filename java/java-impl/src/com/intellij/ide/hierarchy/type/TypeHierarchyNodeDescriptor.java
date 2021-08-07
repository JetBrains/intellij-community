// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class TypeHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  public TypeHierarchyNodeDescriptor(@NotNull Project project, HierarchyNodeDescriptor parentDescriptor, @NotNull PsiElement classOrFunctionalExpression, boolean isBase) {
    super(project, parentDescriptor, classOrFunctionalExpression, isBase);
  }

  public PsiElement getPsiClass() {
    return getPsiElement();
  }

  @Override
  public boolean update() {
    boolean changes = super.update();

    if (getPsiElement() == null) {
      return invalidElement();
    }

    if (changes && myIsBase) {
      setIcon(getBaseMarkerIcon(getIcon()));
    }

    PsiElement psiElement = getPsiClass();

    CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();

    TextAttributes classNameAttributes = null;
    if (myColor != null) {
      classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (psiElement instanceof PsiClass) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)psiElement, false), classNameAttributes);
      myHighlightedText.getEnding().addText(" (" + JavaHierarchyUtil.getPackageName((PsiClass)psiElement) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    } else if (psiElement instanceof PsiFunctionalExpression) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getFunctionalExpressionPresentation(((PsiFunctionalExpression)psiElement), false));
    }
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

}

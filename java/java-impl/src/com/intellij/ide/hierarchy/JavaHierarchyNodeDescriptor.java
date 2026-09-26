// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Represents a descriptor node in a hierarchy specifically designed for Java elements.
/// Extends the [HierarchyNodeDescriptor] to add functionality tailored for handling
/// Java-specific PSI elements such as classes and functional expressions.
/// This class is used as a base to provide rich presentations for Java elements
/// in various hierarchy views (e.g., type hierarchy, method hierarchy, call hierarchy).
public class JavaHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  protected JavaHierarchyNodeDescriptor(@NotNull Project project,
                                        @Nullable NodeDescriptor parentDescriptor,
                                        @NotNull PsiElement element,
                                        boolean isBase) {
    super(project, parentDescriptor, element, isBase);
  }

  /// Recalculates the highlighted text of the hierarchy node descriptor based on the provided PsiElement.
  /// Updates the internal state of the highlighted text and name.
  ///
  /// @param psiElement the PSI element used to calculate the new highlighted text appearance
  /// @return true if the highlighted text has changed; false otherwise
  protected boolean recalculateHighlightedText(PsiElement psiElement) {
    CompositeAppearance oldText = myHighlightedText;
    myHighlightedText = calculateAppearance(psiElement);
    myName = myHighlightedText.getText();
    return !Comparing.equal(myHighlightedText, oldText);
  }

  private @NotNull CompositeAppearance calculateAppearance(PsiElement psiElement) {
    CompositeAppearance appearance = new CompositeAppearance();
    if (psiElement instanceof PsiClass psiClass) {
      appearance.getEnding().addText(ClassPresentationUtil.getSimpleNameForClass(psiClass), textAttributesFor(psiClass));
      appendLocationPath(appearance, psiElement);
    }
    else if (psiElement instanceof PsiFunctionalExpression functionalExpression) {
      appearance.getEnding().addText(ClassPresentationUtil.getSimpleFunctionalExpressionPresentation(functionalExpression),
                                     textAttributesFor(functionalExpression));
      appendLocationPath(appearance, psiElement);
    }
    return appearance;
  }

  /// Appends the location path of the given PSI element to the composite appearance.
  ///
  /// @param compositeAppearance The composite appearance to which the location path is appended.
  /// @param psiElement          The PSI element whose location path is to be appended.
  public static void appendLocationPath(CompositeAppearance compositeAppearance, PsiElement psiElement) {
    String locationPath = JavaHierarchyUtil.getElementLocationPath(psiElement);
    String locationText = JavaBundle.message("node.hierarchy.locationPath", locationPath);
    compositeAppearance.getEnding().addText(locationText, getPackageNameAttributes());
  }
}

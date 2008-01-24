package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DefaultRefactoringElementDescriptionProvider implements ElementDescriptionProvider {
  public static final DefaultRefactoringElementDescriptionProvider INSTANCE = new DefaultRefactoringElementDescriptionProvider();

  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    final String typeString = UsageViewUtil.getType(element);
    final String name = UsageViewUtil.getDescriptiveName(element);
    return typeString + " " + CommonRefactoringUtil.htmlEmphasize(name);
  }
}

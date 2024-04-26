// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;


public final class DefaultRefactoringElementDescriptionProvider implements ElementDescriptionProvider {
  public static final DefaultRefactoringElementDescriptionProvider INSTANCE = new DefaultRefactoringElementDescriptionProvider();

  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    final String typeString = UsageViewUtil.getType(element);
    final String name = DescriptiveNameUtil.getDescriptiveName(element);
    return typeString + " " + CommonRefactoringUtil.htmlEmphasize(name);
  }
}

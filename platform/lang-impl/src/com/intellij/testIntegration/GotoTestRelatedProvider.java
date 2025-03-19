// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@InternalIgnoreDependencyViolation
final class GotoTestRelatedProvider extends GotoRelatedProvider {
  @Override
  public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file == null) return Collections.emptyList();

    Collection<PsiElement> result;
    final boolean isTest = TestFinderHelper.isTest(file);
    if (isTest) {
      result = TestFinderHelper.findClassesForTest(file);
    }
    else {
      result = TestFinderHelper.findTestsForClass(file);
    }

    if (!result.isEmpty()) {
      final List<GotoRelatedItem> items = new ArrayList<>();
      for (PsiElement element : result) {
        String group = isTest ? CodeInsightBundle.message("separator.goto.tested.classes") : CodeInsightBundle.message("separator.goto.tests");
        items.add(new GotoRelatedItem(element, group));
      }
      return items;
    }
    return Collections.emptyList();
  }
}
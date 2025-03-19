// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public abstract class BackgroundUpdaterTask extends BackgroundUpdaterTaskBase<PsiElement> {

  public BackgroundUpdaterTask(@Nullable Project project, @ProgressTitle @NotNull String title, @Nullable Comparator<? super PsiElement> comparator) {
    super(project, title, comparator);
  }

  protected static Comparator<PsiElement> createComparatorWrapper(@NotNull Comparator<? super PsiElement> comparator) {
    return (o1, o2) -> {
      int diff = comparator.compare(o1, o2);
      if (diff == 0) {
        return ReadAction.compute(() -> PsiUtilCore.compareElementsByPosition(o1, o2));
      }
      return diff;
    };
  }

  @Override
  protected Usage createUsage(PsiElement element) {
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }

  @Deprecated(forRemoval = true)
  @Override
  public boolean updateComponent(@NotNull PsiElement element, @Nullable Comparator comparator) {
    //Ensures that method with signature `updateComponent(PsiElement, Comparator)` is present in bytecode,
    //which is necessary for binary compatibility with some external plugins.
    return super.updateComponent(element, comparator);
  }

  @SuppressWarnings("RedundantMethodOverride")
  @Override
  public boolean updateComponent(@NotNull PsiElement element) {
    //Ensures that method with signature `updateComponent(PsiElement)` is present in bytecode,
    //which is necessary for binary compatibility with some external plugins.
    return super.updateComponent(element);
  }

  @Override
  protected @Nullable PsiElement getTheOnlyOneElement() {
    return super.getTheOnlyOneElement();
  }
}


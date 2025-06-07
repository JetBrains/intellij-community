// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.uast.UastVisitorAdapter;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.pane;

public final class MarkedForRemovalInspection extends DeprecationInspectionBase {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    DeprecatedApiUsageProcessor processor =
      new DeprecatedApiUsageProcessor(holder, false, false, false, false, IGNORE_IN_SAME_OUTERMOST_CLASS, true);
    return new UastVisitorAdapter(new ApiUsageUastVisitor(processor), true);
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return "";
  }

  @Override
  public @NotNull String getShortName() {
    return DeprecationUtil.FOR_REMOVAL_SHORT_NAME;
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull @NonNls String getID() {
    return DeprecationUtil.FOR_REMOVAL_ID;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(getSameOutermostClassCheckBox());
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Tells the rename of a Java class on ModPlan which usages out of Java come through an import alias of another language.
 * The rename gives such a usage no edit.
 */
@ApiStatus.Internal
public interface ModPlanRenameAliasFilter {
  ExtensionPointName<ModPlanRenameAliasFilter> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.modPlanRenameAliasFilter");

  /**
   * @return whether {@code usage} is a reference whose name is not the name of {@code renamedClass}, because it comes through an import
   * alias of the language that this filter serves
   */
  boolean isAliasReference(@NotNull UsageInfo usage, @NotNull PsiClass renamedClass);
}

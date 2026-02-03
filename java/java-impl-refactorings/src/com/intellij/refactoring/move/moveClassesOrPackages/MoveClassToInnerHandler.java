// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
@ApiStatus.Internal
public interface MoveClassToInnerHandler {
  ExtensionPointName<MoveClassToInnerHandler> EP_NAME = new ExtensionPointName<>("com.intellij.refactoring.moveClassToInnerHandler");

  @Nullable
  PsiClass moveClass(@NotNull PsiClass aClass, @NotNull PsiClass targetClass);

  /**
   * filters out import usages from results. Returns all found import usages
   */
  @Contract(mutates = "param1")
  List<PsiElement> filterImports(@NotNull List<UsageInfo> usageInfos, @NotNull Project project);

  void retargetClassRefsInMoved(@NotNull Map<PsiElement, PsiElement> mapping);

  void retargetNonCodeUsages(final @NotNull Map<PsiElement, PsiElement> oldToNewElementMap, NonCodeUsageInfo @NotNull [] myNonCodeUsages);

  void removeRedundantImports(PsiFile targetClassFile);
}

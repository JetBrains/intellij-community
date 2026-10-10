// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.refactoring.move.moveClassesOrPackages.ModifyModuleStatementUsageInfo;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class SafeDeleteModuleStatementsUsageInfo extends UsageInfo implements SafeDeleteCustomUsageInfo {
  private final List<ModifyModuleStatementUsageInfo> myModuleStatementUsages = new SmartList<>();

  public SafeDeleteModuleStatementsUsageInfo(@NotNull PsiElement element, @NotNull List<ModifyModuleStatementUsageInfo> usages) {
    super(element);
    myModuleStatementUsages.addAll(usages);
  }

  /** @return the {@code exports} and {@code opens} statements of the package that has no class left */
  public @NotNull List<PsiPackageAccessibilityStatement> getModuleStatements() {
    return ContainerUtil.mapNotNull(myModuleStatementUsages, ModifyModuleStatementUsageInfo::getModuleStatement);
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    Map<PsiJavaModule, List<ModifyModuleStatementUsageInfo>> moduleStatementsByDescriptor = StreamEx.of(myModuleStatementUsages)
      .select(ModifyModuleStatementUsageInfo.class).filter(usage -> usage.getModuleDescriptor() != null)
      .groupingBy(ModifyModuleStatementUsageInfo::getModuleDescriptor);
    MoveClassesOrPackagesProcessor.modifyModuleStatements(moduleStatementsByDescriptor);
  }
}

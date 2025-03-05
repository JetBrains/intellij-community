// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModifyModuleStatementUsageInfo extends UsageInfo {
  private final PsiJavaModule myModuleDescriptor;
  private final ModifyingOperation myModifyingOperation;

  private ModifyModuleStatementUsageInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                         @NotNull PsiJavaModule descriptor,
                                         @NotNull ModifyingOperation modifyingOperation) {
    super(moduleStatement);
    myModuleDescriptor = descriptor;
    myModifyingOperation = modifyingOperation;
  }

  public @NotNull PsiJavaModule getModuleDescriptor() {
    return myModuleDescriptor;
  }

  public @Nullable PsiPackageAccessibilityStatement getModuleStatement() {
    return (PsiPackageAccessibilityStatement)getElement();
  }

  public static @NotNull ModifyModuleStatementUsageInfo createAdditionInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                                                           @NotNull PsiJavaModule descriptor) {
    return new ModifyModuleStatementUsageInfo(moduleStatement, descriptor, ModifyingOperation.ADD);
  }

  public static @NotNull ModifyModuleStatementUsageInfo createDeletionInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                                                           @NotNull PsiJavaModule descriptor) {
    return new ModifyModuleStatementUsageInfo(moduleStatement, descriptor, ModifyingOperation.DELETE);
  }

  public static @NotNull ModifyModuleStatementUsageInfo createLastDeletionInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                                                               @NotNull PsiJavaModule descriptor) {
    return new ModifyModuleStatementUsageInfo(moduleStatement, descriptor, ModifyingOperation.DELETE_LAST);
  }

  boolean isAddition() {
    return myModifyingOperation == ModifyingOperation.ADD;
  }

  boolean isDeletion() {
    return myModifyingOperation == ModifyingOperation.DELETE;
  }

  boolean isLastDeletion() {
    return myModifyingOperation == ModifyingOperation.DELETE_LAST;
  }

  private enum ModifyingOperation {
    ADD, DELETE, DELETE_LAST
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MergePackageAccessibilityStatementsFix
  extends MergeModuleStatementsFix<PsiPackageAccessibilityStatement> {

  private final String myPackageName;
  private final Role myRole;

  protected MergePackageAccessibilityStatementsFix(@NotNull PsiJavaModule javaModule, @NotNull String packageName, @NotNull Role role) {
    super(javaModule);
    myPackageName = packageName;
    myRole = role;
  }

  @Override
  public @Nls @NotNull String getText() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.name", getKeyword(), myPackageName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.family.name", getKeyword());
  }

  @Override
  protected @NotNull String getReplacementText(@NotNull List<? extends PsiPackageAccessibilityStatement> statementsToMerge) {
    final List<String> moduleNames = getModuleNames(statementsToMerge);
    if (!moduleNames.isEmpty()) {
      return getKeyword() + ' ' + myPackageName + ' ' + JavaKeywords.TO + ' ' + joinUniqueNames(moduleNames);
    }
    else {
      return getKeyword() + ' ' + myPackageName;
    }
  }

  private static @NotNull List<String> getModuleNames(@NotNull List<? extends PsiPackageAccessibilityStatement> statements) {
    final List<String> result = new ArrayList<>();
    for (PsiPackageAccessibilityStatement statement : statements) {
      final List<String> moduleNames = statement.getModuleNames();
      if (moduleNames.isEmpty()) {
        return Collections.emptyList();
      }
      result.addAll(moduleNames);
    }
    return result;
  }

  @Override
  protected @NotNull List<PsiPackageAccessibilityStatement> getStatementsToMerge(@NotNull PsiJavaModule javaModule) {
    return StreamEx.of(getStatements(javaModule, myRole).iterator())
      .filter(statement -> myPackageName.equals(statement.getPackageName()))
      .toList();
  }

  public static @Nullable MergeModuleStatementsFix<?> createFix(@Nullable PsiPackageAccessibilityStatement statement) {
    if (statement != null) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiJavaModule) {
        final String packageName = statement.getPackageName();
        if (packageName != null) {
          return new MergePackageAccessibilityStatementsFix((PsiJavaModule)parent, packageName, statement.getRole());
        }
      }
    }
    return null;
  }

  private static @NotNull Iterable<PsiPackageAccessibilityStatement> getStatements(@NotNull PsiJavaModule javaModule, @NotNull Role role) {
    return switch (role) {
      case OPENS -> javaModule.getOpens();
      case EXPORTS -> javaModule.getExports();
    };
  }

  private @NotNull String getKeyword() {
    return switch (myRole) {
      case OPENS -> JavaKeywords.OPENS;
      case EXPORTS -> JavaKeywords.EXPORTS;
    };
  }
}

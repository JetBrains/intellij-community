// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class MergePackageAccessibilityStatementsFix
  extends MergeModuleStatementsFix<PsiPackageAccessibilityStatement> {

  private static final Logger LOG = Logger.getInstance(MergePackageAccessibilityStatementsFix.class);
  private final String myPackageName;
  private final Role myRole;

  protected MergePackageAccessibilityStatementsFix(@NotNull PsiJavaModule javaModule, @NotNull String packageName, @NotNull Role role) {
    super(javaModule);
    myPackageName = packageName;
    myRole = role;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.name", getKeyword(), myPackageName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.family.name", getKeyword());
  }

  @NotNull
  @Override
  protected String getReplacementText(@NotNull List<PsiPackageAccessibilityStatement> statementsToMerge) {
    final List<String> moduleNames = getModuleNames(statementsToMerge);
    if (!moduleNames.isEmpty()) {
      return getKeyword() + ' ' + myPackageName + ' ' + PsiKeyword.TO + ' ' + joinUniqueNames(moduleNames);
    }
    else {
      return getKeyword() + ' ' + myPackageName;
    }
  }

  @NotNull
  private static List<String> getModuleNames(@NotNull List<PsiPackageAccessibilityStatement> statements) {
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

  @NotNull
  @Override
  protected List<PsiPackageAccessibilityStatement> getStatementsToMerge(@NotNull PsiJavaModule javaModule) {
    return StreamEx.of(getStatements(javaModule, myRole).iterator())
      .filter(statement -> myPackageName.equals(statement.getPackageName()))
      .toList();
  }

  @Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiPackageAccessibilityStatement statement) {
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

  @NotNull
  private static Iterable<PsiPackageAccessibilityStatement> getStatements(@NotNull PsiJavaModule javaModule, @NotNull Role role) {
    switch (role) {
      case OPENS:
        return javaModule.getOpens();
      case EXPORTS:
        return javaModule.getExports();
    }
    LOG.error("Unexpected role " + role);
    return Collections.emptyList();
  }

  @NotNull
  private String getKeyword() {
    switch (myRole) {
      case OPENS:
        return PsiKeyword.OPENS;
      case EXPORTS:
        return PsiKeyword.EXPORTS;
    }
    LOG.error("Unexpected role " + myRole);
    return "";
  }
}

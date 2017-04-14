/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class MergePackageAccessibilityStatementsFix
  extends MergeModuleStatementsFix<PsiPackageAccessibilityStatement> {

  private static final Logger LOG = Logger.getInstance(MergePackageAccessibilityStatementsFix.class);
  private final String myPackageName;
  private final List<String> myModuleNames;
  private final Role myRole;

  protected MergePackageAccessibilityStatementsFix(@NotNull PsiPackageAccessibilityStatement thisStatement,
                                                   @NotNull String packageName,
                                                   @NotNull List<String> moduleNames,
                                                   @NotNull PsiPackageAccessibilityStatement otherStatement) {
    super(thisStatement, otherStatement);
    myPackageName = packageName;
    myModuleNames = moduleNames;
    myRole = thisStatement.getRole();
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
  protected String getReplacementText(@NotNull PsiPackageAccessibilityStatement otherStatement) {
    return getKeyword() + " " + myPackageName + " " + PsiKeyword.TO + " " +
           joinNames(otherStatement.getModuleNames(), myModuleNames) + ";";
  }

  @NotNull
  @Override
  protected Iterable<PsiPackageAccessibilityStatement> getStatements(@NotNull PsiJavaModule javaModule) {
    return getStatements(javaModule, myRole);
  }

  @Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiPackageAccessibilityStatement statement) {
    if (statement != null) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiJavaModule) {
        final PsiJavaModule javaModule = (PsiJavaModule)parent;

        final String packageName = statement.getPackageName();
        if (packageName != null) {
          final List<String> moduleNames = statement.getModuleNames();
          if (!moduleNames.isEmpty()) {
            PsiPackageAccessibilityStatement targetStatement = null;
            for (PsiPackageAccessibilityStatement candidate : getStatements(javaModule, statement.getRole())) {
              if (candidate != statement && packageName.equals(candidate.getPackageName())) {
                if (candidate.getModuleNames().isEmpty()) {
                  // merging with a statement that has no target modules is equivalent to deletion; deletion is a different fix
                  return null;
                }
                if (targetStatement == null) {
                  targetStatement = candidate;
                }
              }
            }
            if (targetStatement != null) {
              return new MergePackageAccessibilityStatementsFix(statement, packageName, moduleNames, targetStatement);
            }
          }
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

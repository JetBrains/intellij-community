// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiProvidesStatement;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MergeProvidesStatementsFix extends MergeModuleStatementsFix<PsiProvidesStatement> {
  private final String myInterfaceName;

  MergeProvidesStatementsFix(@NotNull PsiJavaModule javaModule, @NotNull String interfaceName) {
    super(javaModule);
    myInterfaceName = interfaceName;
  }

  @Override
  public @NotNull String getText() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.name", JavaKeywords.PROVIDES, myInterfaceName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.family.name", JavaKeywords.PROVIDES);
  }

  @Override
  protected @NotNull String getReplacementText(@NotNull List<? extends PsiProvidesStatement> statementsToMerge) {
    final List<String> implementationNames = getImplementationNames(statementsToMerge);
    LOG.assertTrue(!implementationNames.isEmpty());
    return JavaKeywords.PROVIDES + ' ' + myInterfaceName + ' ' + JavaKeywords.WITH + ' ' + joinUniqueNames(implementationNames);
  }

  private static @NotNull List<String> getImplementationNames(@NotNull List<? extends PsiProvidesStatement> statements) {
    return StreamEx.of(statements)
      .map(PsiProvidesStatement::getImplementationList)
      .nonNull()
      .flatMap(implementationList -> Arrays.stream(implementationList.getReferenceElements()))
      .nonNull()
      .map(PsiJavaCodeReferenceElement::getQualifiedName)
      .nonNull()
      .toList();
  }

  @Override
  protected @NotNull List<PsiProvidesStatement> getStatementsToMerge(@NotNull PsiJavaModule javaModule) {
    return StreamEx.of(javaModule.getProvides().iterator())
      .filter(statement -> {
        final PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
        return reference != null && myInterfaceName.equals(reference.getQualifiedName());
      })
      .toList();
  }

  public static @Nullable MergeModuleStatementsFix<?> createFix(@Nullable PsiProvidesStatement statement) {
    if (statement != null) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiJavaModule) {
        final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
        if (interfaceReference != null) {
          final String interfaceName = interfaceReference.getQualifiedName();
          if (interfaceName != null) {
            return new MergeProvidesStatementsFix((PsiJavaModule)parent, interfaceName);
          }
        }
      }
    }
    return null;
  }
}

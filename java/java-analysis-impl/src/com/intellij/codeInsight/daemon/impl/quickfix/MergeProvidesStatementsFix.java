// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class MergeProvidesStatementsFix extends MergeModuleStatementsFix<PsiProvidesStatement> {
  private final String myInterfaceName;

  MergeProvidesStatementsFix(@NotNull PsiJavaModule javaModule, @NotNull String interfaceName) {
    super(javaModule);
    myInterfaceName = interfaceName;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.name", PsiKeyword.PROVIDES, myInterfaceName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("java.9.merge.module.statements.fix.family.name", PsiKeyword.PROVIDES);
  }

  @NotNull
  @Override
  protected String getReplacementText(@NotNull List<PsiProvidesStatement> statementsToMerge) {
    final List<String> implementationNames = getImplementationNames(statementsToMerge);
    LOG.assertTrue(!implementationNames.isEmpty());
    return PsiKeyword.PROVIDES + ' ' + myInterfaceName + ' ' + PsiKeyword.WITH + ' ' + joinUniqueNames(implementationNames);
  }

  @NotNull
  private static List<String> getImplementationNames(@NotNull List<PsiProvidesStatement> statements) {
    return StreamEx.of(statements)
      .map(PsiProvidesStatement::getImplementationList)
      .nonNull()
      .flatMap(implementationList -> Arrays.stream(implementationList.getReferenceElements()))
      .nonNull()
      .map(PsiJavaCodeReferenceElement::getQualifiedName)
      .nonNull()
      .toList();
  }

  @NotNull
  @Override
  protected List<PsiProvidesStatement> getStatementsToMerge(@NotNull PsiJavaModule javaModule) {
    return StreamEx.of(javaModule.getProvides().iterator())
      .filter(statement -> {
        final PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
        return reference != null && myInterfaceName.equals(reference.getQualifiedName());
      })
      .toList();
  }

  @Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiProvidesStatement statement) {
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

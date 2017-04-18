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
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
public class MergeProvidesStatementsFix extends MergeModuleStatementsFix<PsiProvidesStatement> {
  private final String myInterfaceName;
  private final List<String> myImplementationNames;

  MergeProvidesStatementsFix(@NotNull PsiProvidesStatement thisStatement,
                             @NotNull String interfaceName,
                             @NotNull List<String> implementationNames,
                             @NotNull PsiProvidesStatement otherStatement) {
    super(thisStatement, otherStatement);
    myInterfaceName = interfaceName;
    myImplementationNames = implementationNames;
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
  protected String getReplacementText(@NotNull PsiProvidesStatement otherStatement) {
    return PsiKeyword.PROVIDES + " " + myInterfaceName + " " + PsiKeyword.WITH + " " +
           joinNames(getImplementationNames(otherStatement), myImplementationNames) + ";";
  }

  @NotNull
  @Override
  protected Iterable<PsiProvidesStatement> getStatements(@NotNull PsiJavaModule javaModule) {
    return javaModule.getProvides();
  }

  @NotNull
  private static List<String> getImplementationNames(@Nullable PsiProvidesStatement statement) {
    if (statement != null) {
      final PsiReferenceList implementationList = statement.getImplementationList();
      if (implementationList != null) {
        return Arrays.stream(implementationList.getReferenceElements())
          .map(PsiJavaCodeReferenceElement::getQualifiedName)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiProvidesStatement statement) {
    if (statement != null) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiJavaModule) {
        final PsiJavaModule javaModule = (PsiJavaModule)parent;

        final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
        if (interfaceReference != null) {
          final String interfaceName = interfaceReference.getQualifiedName();
          if (interfaceName != null) {
            final List<String> implementationNames = getImplementationNames(statement);
            if (!implementationNames.isEmpty()) {
              for (PsiProvidesStatement candidate : javaModule.getProvides()) {
                final PsiJavaCodeReferenceElement candidateInterfaceReference = candidate.getInterfaceReference();
                if (candidateInterfaceReference != null && interfaceName.equals(candidateInterfaceReference.getQualifiedName())) {
                  return new MergeProvidesStatementsFix(statement, interfaceName, implementationNames, candidate);
                }
              }
            }
          }
        }
      }
    }
    return null;
  }
}

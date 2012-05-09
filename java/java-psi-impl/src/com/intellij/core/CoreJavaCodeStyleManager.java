/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CoreJavaCodeStyleManager extends JavaCodeStyleManager {
  @Override
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return false;
  }

  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element,
                                           @MagicConstant(flags = {DO_NOT_ADD_IMPORTS, UNCOMPLETE_CODE}) int flags)
    throws IncorrectOperationException {
    return null;
  }

  @NotNull
  @Override
  public String getPrefixByVariableKind(VariableKind variableKind) {
    return "";
  }

  @NotNull
  @Override
  public String getSuffixByVariableKind(VariableKind variableKind) {
    return "";
  }

  @Override
  public int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    return 0;
  }

  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
  }

  @Override
  public void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException {
  }

  @Override
  public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
    return null;
  }

  @Override
  public VariableKind getVariableKind(@NotNull PsiVariable variable) {
    return null;
  }

  @Override
  public SuggestedNameInfo suggestVariableName(@NotNull VariableKind kind,
                                               @Nullable String propertyName,
                                               @Nullable PsiExpression expr,
                                               @Nullable PsiType type,
                                               boolean correctKeywords) {
    return null;
  }

  @Override
  public String variableNameToPropertyName(@NonNls String name, VariableKind variableKind) {
    return null;
  }

  @Override
  public String propertyNameToVariableName(@NonNls String propertyName, VariableKind variableKind) {
    return null;
  }

  @Override
  public String suggestUniqueVariableName(@NonNls String baseName, PsiElement place, boolean lookForward) {
    return null;
  }

  @NotNull
  @Override
  public SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo,
                                                     PsiElement place,
                                                     boolean ignorePlaceName,
                                                     boolean lookForward) {
    return SuggestedNameInfo.NULL_INFO;
  }

  @Override
  public PsiElement qualifyClassReferences(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void removeRedundantImports(@NotNull PsiJavaFile file) throws IncorrectOperationException {
  }

  @Override
  public Collection<PsiImportStatementBase> findRedundantImports(PsiJavaFile file) {
    return null;
  }
}

/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public abstract class CodeStyleManager {
  public static CodeStyleManager getInstance(Project project){
    return project.getComponent(CodeStyleManager.class);
  }

  public static CodeStyleManager getInstance(PsiManager manager) {
    return getInstance(manager.getProject());
  }

  public abstract Project getProject();

  public abstract PsiElement reformat(PsiElement element) throws IncorrectOperationException;
  public abstract PsiElement reformatRange(PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  public abstract void reformatText(PsiFile element, int startOffset, int endOffset) throws IncorrectOperationException;

  public abstract PsiElement reformatRange(PsiElement element,
                                           int startOffset,
                                           int endOffset,
                                           boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException;
  public abstract PsiElement shortenClassReferences(PsiElement element) throws IncorrectOperationException;
  public abstract void shortenClassReferences(PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  public abstract void optimizeImports(PsiFile file) throws IncorrectOperationException;
  public abstract PsiImportList prepareOptimizeImportsResult(PsiFile file);

  public abstract void adjustLineIndent(PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException;
  public abstract int adjustLineIndent(PsiFile file, int offset) throws IncorrectOperationException;
  public abstract boolean isLineToBeIndented(PsiFile file, int offset);
  @Nullable
  public abstract String getLineIndent(PsiFile file, int offset);

  @Nullable
  public abstract String getLineIndent(Editor editor);

  public abstract Indent getIndent(String text, FileType fileType);
  public abstract String fillIndent(Indent indent, FileType fileType);
  public abstract Indent zeroIndent();

  public abstract PsiElement insertNewLineIndentMarker(PsiFile file, int offset) throws IncorrectOperationException;

  public abstract VariableKind getVariableKind(PsiVariable variable);

  public abstract SuggestedNameInfo suggestVariableName(VariableKind kind, String propertyName, PsiExpression expr, PsiType type);

  public abstract String variableNameToPropertyName(String name, VariableKind variableKind);
  public abstract String propertyNameToVariableName(String propertyName, VariableKind variableKind);

  public abstract String suggestUniqueVariableName(String baseName, PsiElement place, boolean lookForward);

  public abstract boolean checkIdentifierRole(String identifier, IdentifierRole role);

  public abstract PsiElement qualifyClassReferences(PsiElement element);

  public abstract void removeRedundantImports(PsiJavaFile file) throws IncorrectOperationException;
}

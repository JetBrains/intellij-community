/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

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
  public abstract PsiElement shortenClassReferences(PsiElement element) throws IncorrectOperationException;
  public abstract void shortenClassReferences(PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  public abstract void optimizeImports(PsiFile file) throws IncorrectOperationException;
  public abstract PsiImportList prepareOptimizeImportsResult(PsiFile file);

  public abstract int adjustLineIndent(PsiFile file, int offset) throws IncorrectOperationException;
  public abstract boolean isLineToBeIndented(PsiFile file, int offset);

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
}

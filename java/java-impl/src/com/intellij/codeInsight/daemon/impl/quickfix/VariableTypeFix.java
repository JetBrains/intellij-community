/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix");

  private final PsiType myReturnType;
  protected final String myName;

  public VariableTypeFix(@NotNull PsiVariable variable, PsiType toReturn) {
    super(variable);
    myReturnType = toReturn != null ? GenericsUtil.getVariableTypeByExpressionType(toReturn) : null;
    myName = variable.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  myName,
                                  getReturnType().getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    return myVariable.isValid()
        && myVariable.getManager().isInProject(myVariable)
        && getReturnType() != null
        && getReturnType().isValid()
        && !TypeConversionUtil.isNullType(getReturnType())
        && !TypeConversionUtil.isVoidType(getReturnType());
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    if (!CodeInsightUtilBase.prepareFileForWrite(myVariable.getContainingFile())) return;
    try {
      myVariable.normalizeDeclaration();
      myVariable.getTypeElement().replace(JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(
          getReturnType()));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
      UndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected PsiType getReturnType() {
    return myReturnType;
  }
}

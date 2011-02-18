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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
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

public class VariableTypeFix extends IntentionAndQuickFixAction {
  static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix");

  private final PsiVariable myVariable;
  private final PsiType myReturnType;

  public VariableTypeFix(PsiVariable variable, PsiType toReturn) {
    myVariable = variable;
    myReturnType = toReturn != null ? GenericsUtil.getVariableTypeByExpressionType(toReturn) : null;
  }


  @NotNull
  @Override
  public String getName() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  getVariable().getName(),
                                  getReturnType().getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getVariable() != null
        && getVariable().isValid()
        && getVariable().getManager().isInProject(getVariable())
        && getReturnType() != null
        && getReturnType().isValid()
        && !TypeConversionUtil.isNullType(getReturnType())
        && !TypeConversionUtil.isVoidType(getReturnType());
  }

  @Override
  public void applyFix(Project project, PsiFile file, @Nullable Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(getVariable().getContainingFile())) return;
    try {
      getVariable().normalizeDeclaration();
      getVariable().getTypeElement().replace(JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(
          getReturnType()));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(getVariable());
      UndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected PsiVariable getVariable() {
    return myVariable;
  }

  protected PsiType getReturnType() {
    return myReturnType;
  }
}

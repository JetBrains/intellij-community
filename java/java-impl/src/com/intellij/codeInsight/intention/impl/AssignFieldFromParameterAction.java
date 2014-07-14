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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssignFieldFromParameterAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(AssignFieldFromParameterAction.class);

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    final PsiType type = FieldFromParameterUtils.getType(myParameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (!FieldFromParameterUtils.isAvailable(myParameter, type, targetClass)) {
      return false;
    }
    final PsiField field = findFieldToAssign(project, myParameter);
    if (field == null) return false;
    if (!field.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    setText(CodeInsightBundle.message("intention.assign.field.from.parameter.text", field.getName()));

    return true;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.assign.field.from.parameter.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    try {
      PsiField field = findFieldToAssign(project, myParameter);
      if (field != null) addFieldAssignmentStatement(project, field, myParameter, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiField findFieldToAssign(@NotNull Project project,
                                            @NotNull PsiParameter myParameter) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    final String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    final SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, FieldFromParameterUtils.getSubstitutedType(myParameter));

    final String fieldName = suggestedNameInfo.names[0];

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiField field = aClass.findFieldByName(fieldName, false);
    if (field == null) return null;
    if (!field.hasModifierProperty(PsiModifier.STATIC) && isMethodStatic) return null;

    return field;
  }

  public static PsiElement addFieldAssignmentStatement(@NotNull Project project,
                                                       @NotNull PsiField field,
                                                       @NotNull PsiParameter parameter,
                                                       @NotNull Editor editor) throws IncorrectOperationException {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String fieldName = field.getName();
    final String parameterName = parameter.getName();
    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return null;

    String stmtText = fieldName + " = " + parameterName + ";";
    if (Comparing.strEqual(fieldName, parameterName) || JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(fieldName, methodBody) != field) {
      @NonNls String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
      stmtText = prefix + stmtText;
    }

    final PsiStatement assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(factory.createStatementFromText(stmtText, methodBody));
    final PsiStatement[] statements = methodBody.getStatements();
    final int i = FieldFromParameterUtils.findFieldAssignmentAnchor(statements, null, targetClass, parameter);
    final PsiElement inserted;
    if (i == statements.length) {
      inserted = methodBody.add(assignmentStmt);
    }
    else {
      inserted = methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
    }
    editor.getCaretModel().moveToOffset(inserted.getTextRange().getEndOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return inserted;
  }
}

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
import com.intellij.codeInsight.CodeInsightUtilBase;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction");

  @Nullable
  private static PsiType getType(final PsiParameter myParameter) {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    return type;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter myParameter = CreateFieldFromParameterAction.findParameterAtCursor(file, editor);
    final PsiType type = getType(myParameter);
    PsiClass targetClass = myParameter == null ? null : PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (myParameter == null
        || !myParameter.isValid()
        || !myParameter.getManager().isInProject(myParameter)
        || !(myParameter.getDeclarationScope() instanceof PsiMethod)
        || ((PsiMethod)myParameter.getDeclarationScope()).getBody() == null
        || type == null
        || !type.isValid()
        || targetClass == null
        || targetClass.isInterface()
        || CreateFieldFromParameterAction.isParameterAssignedToField(myParameter)) {
      return false;
    }
    PsiField field = findFieldToAssign(myParameter);
    if (field == null) return false;
    setText(CodeInsightBundle.message("intention.assign.field.from.parameter.text", field.getName()));

    return true;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.assign.field.from.parameter.family");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter myParameter = CreateFieldFromParameterAction.findParameterAtCursor(file, editor);
    if (!CodeInsightUtilBase.prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    try {
      PsiField field = findFieldToAssign(myParameter);
      if (field != null) addFieldAssignmentStatement(project, field, myParameter, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static void addFieldAssignmentStatement(@NotNull Project project,
                                                 @NotNull PsiField field,
                                                 @NotNull PsiParameter parameter,
                                                 @NotNull Editor editor) throws IncorrectOperationException {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return;
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    String fieldName = field.getName();
    String parameterName = parameter.getName();
    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    PsiClass targetClass = method.getContainingClass();

    String stmtText = fieldName + " = " + parameterName + ";";
    if (Comparing.strEqual(fieldName, parameterName) || JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(fieldName, methodBody) != field) {
      @NonNls String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
      stmtText = prefix + stmtText;
    }

    PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
    assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(assignmentStmt);
    PsiStatement[] statements = methodBody.getStatements();
    int i = CreateFieldFromParameterAction.findFieldAssignmentAnchor(statements, null, targetClass, parameter);
    PsiElement inserted;
    if (i == statements.length) {
      inserted = methodBody.add(assignmentStmt);
    }
    else {
      inserted = methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
    }
    editor.getCaretModel().moveToOffset(inserted.getTextRange().getEndOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  @Nullable
  private static PsiField findFieldToAssign(final PsiParameter myParameter) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myParameter.getProject());
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, getType(myParameter));

    final String fieldName = suggestedNameInfo.names[0];

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiField field = aClass.findFieldByName(fieldName, false);
    if (field == null) return null;
    if (!field.hasModifierProperty(PsiModifier.STATIC) && isMethodStatic) return null;

    return field;
  }


}

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

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.JavaBundle;
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
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssignFieldFromParameterAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(AssignFieldFromParameterAction.class);
  private final boolean myIsFix;

  public AssignFieldFromParameterAction() {
    this(false);
  }
  public AssignFieldFromParameterAction(boolean isFix) {
    myIsFix = isFix;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (myParameter == null) return false;
    PsiType type = FieldFromParameterUtils.getType(myParameter);
    PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (!FieldFromParameterUtils.isAvailable(myParameter, type, targetClass)) {
      return false;
    }
    PsiField field = findFieldToAssign(project, myParameter);
    if (field == null || !field.getType().isAssignableFrom(type)) return false;
    if (!field.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    PsiElement scope = myParameter.getDeclarationScope();
    if (scope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)scope;
      PsiCodeBlock body = method.getBody();
      if (body == null) return false;
      if (!myIsFix && !VariableAccessUtils.variableIsUsed(myParameter, body)) {
        // for unused parameter there will be a separate quick fix
        return false;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (!JavaHighlightUtil.getChainedConstructors(method).isEmpty()) return false;
        try {
          ControlFlow flow =
            ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
          if (!ControlFlowUtil.isVariableDefinitelyNotAssigned(field, flow)) return false;
        }
        catch (AnalysisCanceledException ignored) {
        }
      }
    }
    setText(JavaBundle.message("intention.assign.field.from.parameter.text", field.getName()));

    return true;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.assign.field.from.parameter.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    PsiField field = myParameter == null ? null : findFieldToAssign(project, myParameter);
    if (field != null) {
      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
      addFieldAssignmentStatement(project, field, myParameter, editor);
    }
  }

  @Nullable
  private static PsiField findFieldToAssign(@NotNull Project project, @NotNull PsiParameter myParameter) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, FieldFromParameterUtils.getSubstitutedType(myParameter));

    String fieldName = suggestedNameInfo.names[0];

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
    PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String fieldName = field.getName();
    String parameterName = parameter.getName();
    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return null;

    String stmtText = fieldName + " = " + parameterName + ";";
    if (Comparing.strEqual(fieldName, parameterName) || JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(fieldName, methodBody) != field) {
      @NonNls String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
      stmtText = prefix + stmtText;
    }

    PsiStatement assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(factory.createStatementFromText(stmtText, methodBody));
    PsiStatement[] statements = methodBody.getStatements();
    int i = FieldFromParameterUtils.findFieldAssignmentAnchor(statements, null, null, targetClass, parameter);
    PsiElement inserted;
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

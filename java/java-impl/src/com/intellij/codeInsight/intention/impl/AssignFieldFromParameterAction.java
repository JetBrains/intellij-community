// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModPsiNavigator;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Editor;
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

public class AssignFieldFromParameterAction extends PsiUpdateModCommandAction<PsiParameter> {
  private final boolean myIsFix;

  public AssignFieldFromParameterAction() {
    this(false);
  }
  public AssignFieldFromParameterAction(boolean isFix) {
    super(PsiParameter.class);
    myIsFix = isFix;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameter myParameter) {
    PsiType type = FieldFromParameterUtils.getType(myParameter);
    PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (!FieldFromParameterUtils.isAvailable(myParameter, type, targetClass)) {
      return null;
    }
    Project project = context.project();
    PsiField field = findFieldToAssign(project, myParameter);
    if (field == null || !field.getType().isAssignableFrom(type)) return null;
    if (!field.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return null;
    PsiElement scope = myParameter.getDeclarationScope();
    if (scope instanceof PsiMethod method) {
      PsiCodeBlock body = method.getBody();
      if (body == null) return null;
      if (!myIsFix && !VariableAccessUtils.variableIsUsed(myParameter, body)) {
        // for unused parameter there will be a separate quick fix
        return null;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (!JavaHighlightUtil.getChainedConstructors(method).isEmpty()) return null;
        try {
          ControlFlow flow =
            ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
          if (!ControlFlowUtil.isVariableDefinitelyNotAssigned(field, flow)) return null;
        }
        catch (AnalysisCanceledException ignored) {
        }
      }
    }
    return Presentation.of(JavaBundle.message("intention.assign.field.from.parameter.text", field.getName()));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.assign.field.from.parameter.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiParameter myParameter, @NotNull ModPsiUpdater updater) {
    PsiField field = findFieldToAssign(context.project(), myParameter);
    if (field != null) {
      addFieldAssignmentStatement(context.project(), field, myParameter, updater);
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
    return addFieldAssignmentStatement(project, field, parameter, ModPsiNavigator.fromEditor(editor));
  }

  private static PsiElement addFieldAssignmentStatement(@NotNull Project project,
                                                        @NotNull PsiField field,
                                                        @NotNull PsiParameter parameter,
                                                        @Nullable ModPsiNavigator updater) throws IncorrectOperationException {
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
    if (updater != null) {
      updater.moveTo(inserted.getTextRange().getEndOffset());
    }
    return inserted;
  }
}

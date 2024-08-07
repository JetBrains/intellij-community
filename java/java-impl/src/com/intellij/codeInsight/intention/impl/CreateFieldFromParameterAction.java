// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Max Medvedev
 */
public final class CreateFieldFromParameterAction extends PsiUpdateModCommandAction<PsiParameter> {
  private final boolean myIsFix;

  /** intention entry point, see /META-INF/JavaPlugin.xml */
  public CreateFieldFromParameterAction() {
    super(PsiParameter.class);
    myIsFix = false;
  }

  /** quickfix entry point, see {@link com.intellij.codeInsight.intention.QuickFixFactory#createCreateFieldFromParameterFix(PsiParameter)} ()} */
  public CreateFieldFromParameterAction(@NotNull PsiParameter parameter) {
    super(parameter);
    myIsFix = true;
  }

  private boolean isAvailable(@NotNull PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod method)) {
      return false;
    }
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;

    if (!myIsFix && !VariableAccessUtils.variableIsUsed(parameter, body) && isUnusedSymbolInspectionEnabled(body)) {
      // for unused parameter there will be a separate quick fix
      return false;
    }
    final PsiType type = FieldFromParameterUtils.getSubstitutedType(parameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(parameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(parameter, type, targetClass, false) &&
           parameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameter parameter) {
    if (!isAvailable(parameter)) {
      return null;
    }
    return Presentation.of(JavaBundle.message("intention.create.field.from.parameter.text", parameter.getName()));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.create.field.from.parameter.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiParameter parameter, @NotNull ModPsiUpdater updater) {
    Project project = parameter.getProject();
    PsiType type = FieldFromParameterUtils.getSubstitutedType(parameter);
    if (type == null) return;
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return;

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    SuggestedNameInfo uniqueNameInfo = styleManager.suggestUniqueVariableName(suggestedNameInfo, targetClass, true);

    boolean isFinal = !isMethodStatic && method.isConstructor();

    PsiVariable variable = FieldFromParameterUtils.createFieldAndAddAssignment(
      project, targetClass, method, parameter, type, uniqueNameInfo.names[0], isMethodStatic, isFinal);
    assert variable != null;

    updater.rename(variable, List.of(uniqueNameInfo.names));
  }

  private static boolean isUnusedSymbolInspectionEnabled(@NotNull PsiElement element) {
    HighlightDisplayKey unusedSymbolKey = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);
    PsiFile file = element.getContainingFile();
    if (file == null) return false;
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    if (!profile.isToolEnabled(unusedSymbolKey, file)) {
      return false;
    }
    HighlightingLevelManager levelManager = HighlightingLevelManager.getInstance(file.getProject());
    return levelManager.shouldInspect(file);
  }
}

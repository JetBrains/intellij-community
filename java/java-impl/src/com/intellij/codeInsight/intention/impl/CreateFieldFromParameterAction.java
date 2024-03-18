// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public final class CreateFieldFromParameterAction extends CreateFieldFromParameterActionBase {
  private final boolean myIsFix;

  /** intention entry point, see /META-INF/JavaPlugin.xml */
  public CreateFieldFromParameterAction() {
    this(false);
  }

  /** quickfix entry point, see {@link com.intellij.codeInsight.intention.QuickFixFactory#createCreateFieldFromParameterFix()} */
  public CreateFieldFromParameterAction(boolean isFix) {
    myIsFix = isFix;
  }

  @Override
  protected boolean isAvailable(@NotNull PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return false;
    }
    PsiCodeBlock body = ((PsiMethod)scope).getBody();
    if (body == null) return false;

    if (!myIsFix && !VariableAccessUtils.variableIsUsed(parameter, body) && isUnusedSymbolInspectionEnabled(body)) {
      // for unused parameter there will be a separate quick fix
      return false;
    }
    final PsiType type = getSubstitutedType(parameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(parameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(parameter, type, targetClass, false) &&
           parameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
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

  @Override
  protected PsiType getSubstitutedType(@NotNull PsiParameter parameter) {
    return FieldFromParameterUtils.getSubstitutedType(parameter);
  }

  @Override
  protected PsiVariable createField(@NotNull Project project,
                                    @NotNull PsiClass targetClass,
                                    @NotNull PsiMethod method,
                                    @NotNull PsiParameter myParameter,
                                    PsiType type,
                                    @NotNull String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    return FieldFromParameterUtils.createFieldAndAddAssignment(project, targetClass, method, myParameter, type, fieldName, methodStatic, isFinal);
  }
}

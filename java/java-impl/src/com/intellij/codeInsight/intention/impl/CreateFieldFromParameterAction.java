/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
public class CreateFieldFromParameterAction extends CreateFieldFromParameterActionBase {
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
  protected void performRefactoring(@NotNull Project project,
                                    @NotNull PsiClass targetClass,
                                    @NotNull PsiMethod method,
                                    @NotNull PsiParameter myParameter,
                                    PsiType type,
                                    @NotNull String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    FieldFromParameterUtils.createFieldAndAddAssignment(project, targetClass, method, myParameter, type, fieldName, methodStatic, isFinal);
  }
}

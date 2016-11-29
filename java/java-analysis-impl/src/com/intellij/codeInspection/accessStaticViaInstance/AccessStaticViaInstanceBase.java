/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class AccessStaticViaInstanceBase extends BaseJavaBatchLocalInspectionTool implements CleanupLocalInspectionTool {
  @NonNls public static final String ACCESS_STATIC_VIA_INSTANCE = "AccessStaticViaInstance";

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("access.static.via.instance");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return ACCESS_STATIC_VIA_INSTANCE;
  }

  @Override
  public String getAlternativeID() {
    return "static-access";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder, isOnTheFly);
      }
    };
  }

  private void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder, boolean onTheFly) {
    JavaResolveResult result = expr.advancedResolve(false);
    PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiMember)) return;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return;

    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement qualifierResolved = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return;
      }
    }
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return;

    String description = JavaErrorMessages.message("static.member.accessed.via.instance.reference",
                                                   JavaHighlightUtil.formatType(qualifierExpression.getType()),
                                                   HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor()));
    if (!onTheFly) {
      if (RemoveUnusedVariableUtil.checkSideEffects(qualifierExpression, null, new ArrayList<>())) {
        holder.registerProblem(expr, description);
        return;
      }
    }
    holder.registerProblem(expr, description, createAccessStaticViaInstanceFix(expr, onTheFly, result));
  }

  protected LocalQuickFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr,
                                                           boolean onTheFly,
                                                           JavaResolveResult result) {
    return null;
  }
}

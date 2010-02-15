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
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
public class AccessStaticViaInstance extends BaseJavaLocalInspectionTool {
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("access.static.via.instance");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "AccessStaticViaInstance";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder);
      }
    };
  }

  private static void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder) {
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
                                                   HighlightUtil.formatType(qualifierExpression.getType()),
                                                   HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor()));
    holder.registerProblem(expr, description, new AccessStaticViaInstanceFix(expr, result));
  }
}

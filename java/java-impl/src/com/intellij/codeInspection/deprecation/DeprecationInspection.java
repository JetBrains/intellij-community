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
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public class DeprecationInspection extends BaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = HighlightInfoType.DEPRECATION_SHORT_NAME;
  @NonNls public static final String ID = HighlightInfoType.DEPRECATION_ID;
  public static final String DISPLAY_NAME = HighlightInfoType.DEPRECATION_DISPLAY_NAME;


  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new DeprecationElementVisitor(holder);
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  @NonNls
  public String getID() {
    return ID;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private static class DeprecationElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public DeprecationElementVisitor(final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        JavaResolveResult result = reference.advancedResolve(true);
        PsiElement resolved = result.getElement();
        checkDeprecated(resolved, reference.getReferenceNameElement(), null, myHolder);
      }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitNewExpression(PsiNewExpression expression) {
      PsiType type = expression.getType();
      PsiExpressionList list = expression.getArgumentList();
      if (!(type instanceof PsiClassType)) return;
      PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = typeResult.getElement();
      if (aClass == null) return;
      if (aClass instanceof PsiAnonymousClass) {
        type = ((PsiAnonymousClass)aClass).getBaseClassType();
        typeResult = ((PsiClassType)type).resolveGenerics();
        aClass = typeResult.getElement();
        if (aClass == null) return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0 && list != null) {
        JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
        MethodCandidateInfo result = null;
        if (results.length == 1) result = (MethodCandidateInfo)results[0];

        PsiMethod constructor = result == null ? null : result.getElement();
        if (constructor != null && expression.getClassReference() != null) {
          checkDeprecated(constructor, expression.getClassReference(), null, myHolder);
        }
      }
    }

    @Override public void visitMethod(PsiMethod method){
        MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
        if (!method.isConstructor()) {
          List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
          checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, myHolder);
        }
      }
  }

  //@top
  static void checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                                          List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                          ProblemsHolder holder) {
    PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (!aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated()) {
        String description = JavaErrorMessages.message("overrides.deprecated.method",
                                                       HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY));
        holder.registerProblem(methodName, description, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  }

  public static void checkDeprecated(PsiElement refElement,
                                     PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     ProblemsHolder holder) {
    if (!(refElement instanceof PsiDocCommentOwner)) return;
    if (!((PsiDocCommentOwner)refElement).isDeprecated()) return;

    String description = JavaErrorMessages.message("deprecated.symbol",
                                                   HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY));

    holder.registerProblem(elementToHighlight, description, ProblemHighlightType.LIKE_DEPRECATED, rangeInElement);
  }
}

/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 * Date: 1/28/11
 */
public class ExplicitTypeCanBeDiamondInspection extends BaseJavaLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Explicit type can be replaced with <>";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2Diamond";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (PsiUtil.getLanguageLevel(expression).isAtLeast(LanguageLevel.JDK_1_7)) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          if (classReference != null) {
            final PsiReferenceParameterList parameterList = classReference.getParameterList();
            if (parameterList != null) {
              final PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();
              if (typeElements.length > 0) {
                if (typeElements.length == 1 && typeElements[0].getType() instanceof PsiDiamondType) return;
                final PsiDiamondType.DiamondInferenceResult inferenceResult = PsiDiamondType.resolveInferredTypes(expression);
                if (inferenceResult.getErrorMessage() == null) {
                  final List<PsiType> types = inferenceResult.getInferredTypes();
                  final PsiType[] typeArguments = parameterList.getTypeArguments();
                  if (types.size() == typeArguments.length) {
                    for (int i = 0, typeArgumentsLength = typeArguments.length; i < typeArgumentsLength; i++) {
                      PsiType typeArgument = typeArguments[i];
                      if (types.get(i) instanceof PsiWildcardType) {
                        final PsiWildcardType wildcardType = (PsiWildcardType)types.get(i);
                        final PsiType bound = wildcardType.getBound();
                        if (bound != null) {
                          if (wildcardType.isExtends()) {
                            if (bound.isAssignableFrom(typeArgument)) continue;
                          } else {
                            if (typeArgument.isAssignableFrom(bound)) continue;
                          }
                        }
                      }
                      if (!typeArgument.equals(types.get(i))) {
                        return;
                      }
                    }
                  }
                  holder.registerProblem(parameterList,  "Redundant type argument #ref #loc",
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithDiamondFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithDiamondFix implements LocalQuickFix, HighPriorityAction {
    public static final Logger LOG = Logger.getInstance("#" + ReplaceWithDiamondFix.class.getName());
    @NotNull
    @Override
    public String getName() {
      return "Replace with <>";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiReferenceParameterList) {
        if (!CodeInsightUtilBase.prepareFileForWrite(psiElement.getContainingFile())) return;
        final PsiNewExpression expression =
          (PsiNewExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("new a<>()", psiElement);
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        LOG.assertTrue(classReference != null);
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        LOG.assertTrue(parameterList != null);
        psiElement.replace(parameterList);
      }
    }
  }
}

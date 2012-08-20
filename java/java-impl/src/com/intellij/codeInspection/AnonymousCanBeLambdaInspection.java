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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class AnonymousCanBeLambdaInspection extends BaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + AnonymousCanBeLambdaInspection.class.getName());

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
    return "Anonymous type can be replaced with lambda";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2Lambda";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        if (PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)&& LambdaUtil.isValidLambdaContext(aClass.getParent().getParent())) {
          final PsiClassType baseClassType = aClass.getBaseClassType();
          final String functionalInterfaceErrorMessage = LambdaUtil.checkInterfaceFunctional(baseClassType);
          if (functionalInterfaceErrorMessage == null) {
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length == 1 && methods[0].getBody() != null) {
              holder.registerProblem(aClass.getBaseClassReference(), "Anonymous #ref #loc can be replaced with lambda",
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithLambdaFix());
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithLambdaFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getName() {
      return "Replace with lambda";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class);
        LOG.assertTrue(anonymousClass != null);

        final PsiMethod method = anonymousClass.getMethods()[0];
        LOG.assertTrue(method != null);

        final String lambdaWithTypesDeclared = composeLambdaText(method, true);
        boolean mustBeFinal = false;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          for (PsiReference reference : ReferencesSearch.search(parameter)) {
            if (HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(parameter, reference.getElement()) != null) {
              mustBeFinal = true;
              break;
            }
          }
          if (mustBeFinal) break;
        }
        PsiLambdaExpression lambdaExpression =
          (PsiLambdaExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText(mustBeFinal ? lambdaWithTypesDeclared : composeLambdaText(method, false), anonymousClass);
        final PsiNewExpression newExpression = (PsiNewExpression)anonymousClass.getParent();
        lambdaExpression = (PsiLambdaExpression)newExpression.replace(lambdaExpression);
        PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
        if (interfaceType == null || !LambdaUtil.isLambdaFullyInferred(lambdaExpression, interfaceType)) {
          lambdaExpression.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(lambdaWithTypesDeclared, lambdaExpression));
        }
      }
    }

    private static String composeLambdaText(PsiMethod method, final boolean appendType) {
      final StringBuilder buf = new StringBuilder();
      if (appendType) {
        buf.append(method.getParameterList().getText());
      } else {
        buf.append("(").append(StringUtil.join(method.getParameterList().getParameters(),
                                               new Function<PsiParameter, String>() {
                                                 @Override
                                                 public String fun(PsiParameter parameter) {
                                                   String parameterName = parameter.getName();
                                                   if (parameterName == null) {
                                                     parameterName = "";
                                                   }
                                                   return parameterName;
                                                 }
                                               }, ",")).append(")");
      }
      buf.append("->");
      final PsiCodeBlock body = method.getBody();
      LOG.assertTrue(body != null);
      buf.append(body.getText());
      return buf.toString();
    }
  }
}

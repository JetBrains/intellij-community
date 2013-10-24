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

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * User: anna
 */
public class AnonymousCanBeLambdaInspection extends BaseJavaBatchLocalInspectionTool {
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
        if (PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)) {
          final PsiClassType baseClassType = aClass.getBaseClassType();
          final String functionalInterfaceErrorMessage = LambdaHighlightingUtil.checkInterfaceFunctional(baseClassType);
          if (functionalInterfaceErrorMessage == null) {
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length == 1 && aClass.getFields().length == 0) {
              final PsiCodeBlock body = methods[0].getBody();
              if (body != null) {
                final boolean [] bodyContainsForbiddenRefs = new boolean[1];
                final Set<PsiLocalVariable> locals = new HashSet<PsiLocalVariable>();
                body.accept(new JavaRecursiveElementWalkingVisitor() {
                  @Override
                  public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
                    if (bodyContainsForbiddenRefs[0]) return;
                    super.visitMethodCallExpression(methodCallExpression);
                    final PsiMethod psiMethod = methodCallExpression.resolveMethod();
                    if (psiMethod == methods[0] ||
                        psiMethod != null &&
                        !methodCallExpression.getMethodExpression().isQualified() &&
                        "getClass".equals(psiMethod.getName()) &&
                        psiMethod.getParameterList().getParametersCount() == 0) {
                      bodyContainsForbiddenRefs[0] = true;
                    }
                  }

                  @Override
                  public void visitThisExpression(PsiThisExpression expression) {
                    if (bodyContainsForbiddenRefs[0]) return;
                    if (expression.getQualifier() == null) {
                      bodyContainsForbiddenRefs[0] = true;
                    }
                  }

                  @Override
                  public void visitSuperExpression(PsiSuperExpression expression) {
                    if (bodyContainsForbiddenRefs[0]) return;
                    if (expression.getQualifier() == null) {
                      bodyContainsForbiddenRefs[0] = true;
                    }
                  }

                  @Override
                  public void visitLocalVariable(PsiLocalVariable variable) {
                    if (bodyContainsForbiddenRefs[0]) return;
                    super.visitLocalVariable(variable);
                    locals.add(variable);
                  }

                  @Override
                  public void visitReferenceExpression(PsiReferenceExpression expression) {
                    if (bodyContainsForbiddenRefs[0]) return;
                    super.visitReferenceExpression(expression);
                    if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
                      final PsiField field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
                      if (field != null) {
                        final PsiElement resolved = expression.resolve();
                        if (resolved instanceof PsiField && 
                            ((PsiField)resolved).hasModifierProperty(PsiModifier.FINAL) && 
                            !((PsiField)resolved).hasInitializer() &&
                            ((PsiField)resolved).getContainingClass() == field.getContainingClass()) {
                          bodyContainsForbiddenRefs[0] = true;
                        }
                      }
                    }
                  }
                });
                if (!bodyContainsForbiddenRefs[0]) {
                  PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(body.getProject());
                  for (PsiLocalVariable local : locals) {
                    final String localName = local.getName();
                    if (localName != null && helper.resolveReferencedVariable(localName, aClass) != null) return;
                  }
                  holder.registerProblem(aClass.getBaseClassReference(), "Anonymous #ref #loc can be replaced with lambda",
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithLambdaFix());
                }
              }
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
        ChangeContextUtil.encodeContextInfo(anonymousClass, true);
        final PsiElement lambdaContext = anonymousClass.getParent().getParent();
        boolean validContext = LambdaUtil.isValidLambdaContext(lambdaContext);
        final String canonicalText = anonymousClass.getBaseClassType().getCanonicalText();
        final PsiMethod method = anonymousClass.getMethods()[0];
        LOG.assertTrue(method != null);

        final String lambdaWithTypesDeclared = composeLambdaText(method, lambdaContext, true);
        final String withoutTypesDeclared = composeLambdaText(method, lambdaContext, false);
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiLambdaExpression lambdaExpression =
          (PsiLambdaExpression)elementFactory.createExpressionFromText(withoutTypesDeclared, anonymousClass);

        final PsiCodeBlock body = method.getBody();
        LOG.assertTrue(body != null);
        final PsiStatement[] statements = body.getStatements();
        PsiElement copy = body.copy();
        if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
          PsiExpression value = ((PsiReturnStatement)statements[0]).getReturnValue();
          if (value != null) {
            copy = value.copy();
          }
        }

        PsiElement lambdaBody = lambdaExpression.getBody();
        LOG.assertTrue(lambdaBody != null);
        lambdaBody.replace(copy);

        final PsiNewExpression newExpression = (PsiNewExpression)anonymousClass.getParent();
        lambdaExpression = (PsiLambdaExpression)newExpression.replace(lambdaExpression);
        ChangeContextUtil.decodeContextInfo(lambdaExpression, null, null);
        if (!validContext) {
          final PsiParenthesizedExpression typeCast =
            (PsiParenthesizedExpression)elementFactory.createExpressionFromText("((" + canonicalText + ")" + withoutTypesDeclared + ")", lambdaExpression);
          final PsiExpression typeCastExpr = typeCast.getExpression();
          LOG.assertTrue(typeCastExpr != null);
          final PsiExpression typeCastOperand = ((PsiTypeCastExpression)typeCastExpr).getOperand();
          LOG.assertTrue(typeCastOperand != null);
          final PsiElement fromText = ((PsiLambdaExpression)typeCastOperand).getBody();
          LOG.assertTrue(fromText != null);
          lambdaBody = lambdaExpression.getBody();
          LOG.assertTrue(lambdaBody != null);
          fromText.replace(lambdaBody);
          lambdaExpression.replace(typeCast);
          return;
        }

        PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
        if (isInferred(lambdaExpression, interfaceType)) {
          final PsiLambdaExpression withTypes =
            (PsiLambdaExpression)elementFactory.createExpressionFromText(lambdaWithTypesDeclared, lambdaExpression);
          final PsiElement withTypesBody = withTypes.getBody();
          LOG.assertTrue(withTypesBody != null);
          lambdaBody = lambdaExpression.getBody();
          LOG.assertTrue(lambdaBody != null);
          withTypesBody.replace(lambdaBody);
          lambdaExpression = (PsiLambdaExpression)lambdaExpression.replace(withTypes);

          interfaceType = lambdaExpression.getFunctionalInterfaceType();
          if (isInferred(lambdaExpression, interfaceType)) {
            final PsiTypeCastExpression typeCast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(" + canonicalText + ")" + withoutTypesDeclared, lambdaExpression);
            final PsiExpression typeCastOperand = typeCast.getOperand();
            LOG.assertTrue(typeCastOperand instanceof PsiLambdaExpression);
            final PsiElement fromText = ((PsiLambdaExpression)typeCastOperand).getBody();
            LOG.assertTrue(fromText != null);
            lambdaBody = lambdaExpression.getBody();
            LOG.assertTrue(lambdaBody != null);
            fromText.replace(lambdaBody);
            lambdaExpression.replace(typeCast);
          }
        }
      }
    }

    private static boolean isInferred(PsiLambdaExpression lambdaExpression, PsiType interfaceType) {
      return interfaceType == null || !LambdaUtil.isLambdaFullyInferred(lambdaExpression, interfaceType) || LambdaHighlightingUtil
                                                                                                              .checkInterfaceFunctional(
                                                                                                                interfaceType) != null;
    }

    private static String composeLambdaText(PsiMethod method, final PsiElement lambdaContext, final boolean appendType) {
      final StringBuilder buf = new StringBuilder();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1 || appendType) {
        buf.append("(");
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(lambdaContext.getProject());
      buf.append(StringUtil.join(parameters,
                                 new Function<PsiParameter, String>() {
                                   @Override
                                   public String fun(PsiParameter parameter) {
                                     return composeParameter(parameter, appendType, codeStyleManager, lambdaContext);
                                   }
                                 }, ","));
      if (parameters.length != 1 || appendType) {
        buf.append(")");
      }
      buf.append("-> {}");
      return buf.toString();
    }

    private static String composeParameter(PsiParameter parameter,
                                           boolean appendType,
                                           JavaCodeStyleManager codeStyleManager,
                                           PsiElement lambdaContext) {
      final String parameterType;
      if (appendType) {
        final PsiTypeElement typeElement = parameter.getTypeElement();
        parameterType = typeElement != null ? (typeElement.getText() + " ") : "";
      }
      else {
        parameterType = "";
      }
      String parameterName = parameter.getName();
      if (parameterName == null) {
        parameterName = "";
      }
      return parameterType + codeStyleManager.suggestUniqueVariableName(parameterName, lambdaContext, true);
    }
  }
}

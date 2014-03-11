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
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
          if (LambdaUtil.isFunctionalType(baseClassType)) {
            final PsiElement lambdaContext = aClass.getParent().getParent();
            if (LambdaUtil.isValidLambdaContext(lambdaContext) || !(lambdaContext instanceof PsiExpressionStatement)) {
              final PsiMethod[] methods = aClass.getMethods();
              if (methods.length == 1 && aClass.getFields().length == 0) {
                final PsiCodeBlock body = methods[0].getBody();
                if (body != null) {
                  final ForbiddenRefsChecker checker = new ForbiddenRefsChecker(methods[0], aClass);
                  body.accept(checker);
                  if (!checker.hasForbiddenRefs()) {
                    PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(body.getProject());
                    for (PsiLocalVariable local : checker.getLocals()) {
                      final String localName = local.getName();
                      if (localName != null && helper.resolveReferencedVariable(localName, aClass) != null) return;
                    }
                    final PsiElement lBrace = aClass.getLBrace();
                    LOG.assertTrue(lBrace != null);
                    final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
                    holder.registerProblem(aClass.getParent(), "Anonymous #ref #loc can be replaced with lambda",
                                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, rangeInElement, new ReplaceWithLambdaFix());
                  }
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
      if (element instanceof PsiNewExpression) {
        final PsiAnonymousClass anonymousClass = ((PsiNewExpression)element).getAnonymousClass();
        LOG.assertTrue(anonymousClass != null);
        ChangeContextUtil.encodeContextInfo(anonymousClass, true);
        final PsiElement lambdaContext = anonymousClass.getParent().getParent();
        boolean validContext = LambdaUtil.isValidLambdaContext(lambdaContext);
        final String canonicalText = anonymousClass.getBaseClassType().getCanonicalText();
        final PsiMethod method = anonymousClass.getMethods()[0];
        LOG.assertTrue(method != null);

        final String lambdaWithTypesDeclared = composeLambdaText(method, true);
        final String withoutTypesDeclared = composeLambdaText(method, false);
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

        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final Map<PsiParameter, String> names = new HashMap<PsiParameter, String>();
        for (PsiParameter parameter : lambdaExpression.getParameterList().getParameters()) {
          String parameterName = parameter.getName();
          final String uniqueVariableName = codeStyleManager.suggestUniqueVariableName(parameterName, lambdaContext, false);
          if (!Comparing.equal(parameterName, uniqueVariableName)) {
            names.put(parameter, uniqueVariableName);
          }
        }

        final LinkedHashMap<PsiElement, PsiElement> replacements = new LinkedHashMap<PsiElement, PsiElement>();
        lambdaExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitParameter(PsiParameter parameter) {
            final String newName = names.get(parameter);
            if (newName != null) {
              replacements.put(parameter.getNameIdentifier(), elementFactory.createIdentifier(newName));
            }
          }

          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement resolve = expression.resolve();
            if (resolve instanceof PsiParameter) {
              final String newName = names.get(resolve);
              if (newName != null) {
                replacements.put(expression, elementFactory.createExpressionFromText(newName, expression));
              }
            }
          }
        });

        for (PsiElement psiElement : replacements.keySet()) {
          psiElement.replace(replacements.get(psiElement));
        }

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
      return interfaceType == null || !LambdaUtil.isLambdaFullyInferred(lambdaExpression, interfaceType) || !LambdaUtil.isFunctionalType(interfaceType);
    }

    private static String composeLambdaText(PsiMethod method, final boolean appendType) {
      final StringBuilder buf = new StringBuilder();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1 || appendType) {
        buf.append("(");
      }
      buf.append(StringUtil.join(parameters,
                                 new Function<PsiParameter, String>() {
                                   @Override
                                   public String fun(PsiParameter parameter) {
                                     return composeParameter(parameter, appendType);
                                   }
                                 }, ","));
      if (parameters.length != 1 || appendType) {
        buf.append(")");
      }
      buf.append("-> {}");
      return buf.toString();
    }

    private static String composeParameter(PsiParameter parameter,
                                           boolean appendType) {
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
      return parameterType + parameterName;
    }
  }

  private static class ForbiddenRefsChecker extends JavaRecursiveElementWalkingVisitor {
    private boolean myBodyContainsForbiddenRefs;
    private final Set<PsiLocalVariable> myLocals = ContainerUtilRt.newHashSet(5);

    private final PsiMethod myMethod;
    private final PsiAnonymousClass myAnonymClass;

    public ForbiddenRefsChecker(PsiMethod method,
                                PsiAnonymousClass aClass) {
      myMethod = method;
      myAnonymClass = aClass;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitMethodCallExpression(methodCallExpression);
      final PsiMethod psiMethod = methodCallExpression.resolveMethod();
      if (psiMethod == myMethod ||
          psiMethod != null &&
          !methodCallExpression.getMethodExpression().isQualified() &&
          "getClass".equals(psiMethod.getName()) &&
          psiMethod.getParameterList().getParametersCount() == 0) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitLocalVariable(variable);
      myLocals.add(variable);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitReferenceExpression(expression);
      if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
        final PsiField field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          final PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField && ((PsiField)resolved).getContainingClass() == field.getContainingClass()) {
            final PsiExpression initializer = ((PsiField)resolved).getInitializer();
            if (initializer == null ||
                initializer.getTextOffset() > myAnonymClass.getTextOffset() && !((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC)) {
              myBodyContainsForbiddenRefs = true;
            }
          }
        } else {
          final PsiMethod method = PsiTreeUtil.getParentOfType(myAnonymClass, PsiMethod.class);
          if (method != null && method.isConstructor()) {
            final PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField && 
                ((PsiField)resolved).hasModifierProperty(PsiModifier.FINAL) &&
                ((PsiField)resolved).getContainingClass() == method.getContainingClass()) {
              try {
                final PsiCodeBlock constructorBody = method.getBody();
                if (constructorBody != null) {
                  final ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(constructorBody);
                  final int startOffset = flow.getStartOffset(myAnonymClass);
                  final Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, startOffset, false);
                  if (!writtenVariables.contains(resolved)) {
                    myBodyContainsForbiddenRefs = true;
                  }
                }
              }
              catch (AnalysisCanceledException e) {
                myBodyContainsForbiddenRefs = true;
              }
            }
          }
        }
      }
    }

    public boolean hasForbiddenRefs() {
      return myBodyContainsForbiddenRefs;
    }

    public Set<PsiLocalVariable> getLocals() {
      return myLocals;
    }
  }
}

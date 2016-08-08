/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class MigrateAssertToMatcherAssertInspection extends LocalInspectionTool {

  private final static Logger LOG = Logger.getInstance(MigrateAssertToMatcherAssertInspection.class);
  private final static Map<String, Pair<String, String>> ASSERT_METHODS = new HashMap<>();

  static {
    ASSERT_METHODS.put("assertArrayEquals", Pair.create("$expected$, $actual$", "$actual$, org.hamcrest.CoreMatchers.is($expected$)"));
    ASSERT_METHODS.put("assertEquals", Pair.create("$expected$, $actual$", "$actual$, org.hamcrest.CoreMatchers.is($expected$)"));
    ASSERT_METHODS.put("assertNotEquals", Pair.create("$expected$, $actual$", "$actual$, org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.is($expected$))"));
    ASSERT_METHODS.put("assertSame", Pair.create("$expected$, $actual$", "$actual$, org.hamcrest.CoreMatchersSame.sameInstance($expected$)"));
    ASSERT_METHODS.put("assertNotSame", Pair.create("$expected$, $actual$", "$actual$, org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchersSame.sameInstance($expected$))"));
    ASSERT_METHODS.put("assertNotNull", Pair.create("$obj$", "$obj$, org.hamcrest.CoreMatchers.notNullValue()"));
    ASSERT_METHODS.put("assertNull", Pair.create("$obj$", "$obj$, org.hamcrest.CoreMatchers.nullValue()"));
    ASSERT_METHODS.put("assertTrue", Pair.create("$cond$", "$cond$, org.hamcrest.CoreMatchers.is(true)"));
    ASSERT_METHODS.put("assertFalse", Pair.create("$cond$", "$cond$, org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.is(false))"));
  }

  public boolean myStaticallyImportMatchers = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Statically import matcher's methods", this, "myStaticallyImportMatchers");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (JavaPsiFacade.getInstance(holder.getProject()).findClass("org.hamcrest.CoreMatchers", holder.getFile().getResolveScope()) == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final String methodName = expression.getMethodExpression().getReferenceName();
        if (!ASSERT_METHODS.containsKey(methodName)) return;
        final PsiClass assertClass;
        final PsiMethod assertMethod = expression.resolveMethod();
        if (assertMethod == null || (assertClass = assertMethod.getContainingClass()) == null) {
          return;
        }
        if (!"junit.framework.Assert".equals(assertClass.getQualifiedName()) &&
            !"org.junit.Assert".equals(assertClass.getQualifiedName())) {
          return;
        }
        holder
          .registerProblem(expression.getMethodExpression(), "Assert expression <code>#ref</code> can be replaced with 'assertThat' call #loc", new MyQuickFix());
      }
    };
  }

  public class MyQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'assertThat'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null || !element.isValid() || !(element.getParent() instanceof PsiMethodCallExpression)) return;
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element.getParent();
      final PsiMethod method = methodCall.resolveMethod();
      if (method == null) {
        return;
      }
      final String methodName = method.getName();
      Pair<String, String> templatePair = null;
      if ("assertFalse".equals(methodName) || "assertTrue".equals(methodName)) {
        final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
        final PsiExpression conditionExpression = expressions[expressions.length - 1];
        final boolean negate = methodName.contains("False");
        if (conditionExpression instanceof PsiBinaryExpression) {
          templatePair = getSuitableMatcherForBinaryExpressionInsideBooleanAssert((PsiBinaryExpression)conditionExpression, negate);
        }
        else if (conditionExpression instanceof PsiMethodCallExpression) {
          templatePair = getSuitableMatcherForMethodCallInsideBooleanAssert((PsiMethodCallExpression)conditionExpression, negate);
        }
      }
      if (templatePair == null) {
        templatePair = ASSERT_METHODS.get(methodName);
      }
      LOG.assertTrue(templatePair != null);
      templatePair = buildFullTemplate(templatePair, method);
      final PsiExpression replaced;
      try {
        replaced = TypeConversionDescriptor.replaceExpression(methodCall, templatePair.getFirst(), templatePair.getSecond());
      }
      catch (IncorrectOperationException e) {
        LOG.error("Replacer can't can't match expression:\n" +
                  methodCall.getText() +
                  "\nwith replacement template:\n(" +
                  templatePair.getFirst() +
                  ", " +
                  templatePair.getSecond() +
                  ")");
        throw e;
      }

      if (myStaticallyImportMatchers) {
        for (PsiJavaCodeReferenceElement ref : ContainerUtil.reverse(
          new ArrayList<>(PsiTreeUtil.findChildrenOfType(replaced, PsiJavaCodeReferenceElement.class)))) {
          if (!ref.isValid()) continue;
          final PsiElement resolvedElement = ref.resolve();
          if (resolvedElement instanceof PsiClass) {
            final String qName = ((PsiClass)resolvedElement).getQualifiedName();
            if (qName != null && qName.startsWith("org.hamcrest")) {
              final PsiIdentifier identifier = PsiTreeUtil.getChildOfType(ref, PsiIdentifier.class);
              if (identifier != null) {
                AddOnDemandStaticImportAction.invoke(project, replaced.getContainingFile(), null, identifier);
              }
            }
          }
        }
      }
    }

    private Pair<String, String> buildFullTemplate(Pair<String, String> templatePair, PsiMethod method) {
      if (templatePair == null) {
        return null;
      }
      final boolean hasMessage = hasMessage(method);
      final String searchTemplate = "'Assert*." + method.getName() + "(" + (hasMessage ? "$msg$, " : "") + templatePair.getFirst() + ")";
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null);
      final String qualifier = containingClass.getQualifiedName();
      LOG.assertTrue(qualifier != null);
      final String replaceTemplate = qualifier + ".assertThat(" + (hasMessage ? "$msg$, " : "") + templatePair.getSecond() + ")";
      return Pair.create(searchTemplate, replaceTemplate);
    }

    @Nullable
    private Pair<String, String> getSuitableMatcherForBinaryExpressionInsideBooleanAssert(PsiBinaryExpression expression, boolean negate) {
      final PsiJavaToken sign = expression.getOperationSign();
      IElementType tokenType = sign.getTokenType();
      if (negate) {
        tokenType = negate(tokenType);
      }
      final String fromTemplate = "$left$ " + sign.getText() + "  $right$";
      if (JavaTokenType.EQEQ.equals(tokenType) || JavaTokenType.NE.equals(tokenType)) {
        boolean isEqEqForPrimitives = true;
        for (PsiExpression operand : ContainerUtil.list(expression.getLOperand(), expression.getROperand())) {
          if (!(operand.getType() instanceof PsiPrimitiveType)) {
            isEqEqForPrimitives = false;
            break;
          }
        }
        String rightPartOfAfterTemplate =
          isEqEqForPrimitives ? "org.hamcrest.CoreMatchers.is($right$)" : "org.hamcrest.CoreMatchers.sameInstance($right$)";
        if (JavaTokenType.NE.equals(tokenType)) {
          rightPartOfAfterTemplate = "org.hamcrest.CoreMatchers.not(" + rightPartOfAfterTemplate + ")";
        }
        return  Pair.create(fromTemplate,
                           "$left$, " + rightPartOfAfterTemplate);
      }
      String replaceTemplate = null;
      if (JavaTokenType.GT.equals(tokenType)) {
        replaceTemplate = "greaterThan($right$)";
      }
      else if (JavaTokenType.LT.equals(tokenType)) {
        replaceTemplate = "lessThan($right$)";
      }
      else if (JavaTokenType.GE.equals(tokenType)) {
        replaceTemplate = "greaterThanOrEqualTo($right$)";
      }
      else if (JavaTokenType.LE.equals(tokenType)) {
        replaceTemplate = "lessThanOrEqualTo($right$)";
      }
      if (replaceTemplate == null) {
        return null;
      }
      replaceTemplate = "org.hamcrest.number.OrderingComparison." + replaceTemplate;
      return Pair.create(fromTemplate, "$left$, " + replaceTemplate);
    }
  }

  private static IElementType negate(IElementType tokenType) {
    if (JavaTokenType.GT.equals(tokenType)) {
      return JavaTokenType.LE;
    }
    else if (JavaTokenType.LT.equals(tokenType)) {
      return JavaTokenType.GE;
    }
    else if (JavaTokenType.GE.equals(tokenType)) {
      return JavaTokenType.LT;
    }
    else if (JavaTokenType.LE.equals(tokenType)) {
      return JavaTokenType.GT;
    }
    return null;
  }

  @Nullable
  private static Pair<String, String> getSuitableMatcherForMethodCallInsideBooleanAssert(PsiMethodCallExpression expression, boolean negate) {
    final String methodName = expression.getMethodExpression().getReferenceName();
    String fromTemplate = null;
    String toLeftPart = null;
    String toRightPart = null;
    if ("contains".equals(methodName)) {
      final PsiMethod method = expression.resolveMethod();
      final PsiClass containingClass;
      if (method != null &&
          (containingClass = method.getContainingClass()) != null) {

        if (CommonClassNames.JAVA_LANG_STRING.equals(containingClass.getQualifiedName())) {
          fromTemplate = "$str$.contains($sub$)";
          toLeftPart = "$str$, ";
          toRightPart = "org.hamcrest.CoreMatchers.containsString($sub$)";
        } else if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          fromTemplate = "$collection$.contains($element$)";
          toLeftPart = "$element$, ";
          toRightPart = "org.hamcrest.CoreMatchers.anyOf($collection$)";
        }
      }
    }
    else if ("equals".equals(methodName)) {
      final PsiMethod method = expression.resolveMethod();
      if (method != null && isUniqueObjectParameter(method.getParameterList())) {
        fromTemplate = "$left$.equals($right$)";
        toLeftPart = "$left$, ";
        toRightPart = "org.hamcrest.CoreMatchers.is($right$)";
      }
    }
    if (fromTemplate == null) {
      return null;
    }
    if (negate) {
      toRightPart = "org.hamcrest.CoreMatchers.not(" + toRightPart + ")";
    }
    return Pair.create(fromTemplate, toLeftPart + toRightPart);
  }

  private static boolean isUniqueObjectParameter(PsiParameterList parameters) {
    if (parameters.getParametersCount() != 1) {
      return false;
    }
    final PsiParameter parameter = parameters.getParameters()[0];
    final PsiClass parameterClass = PsiTypesUtil.getPsiClass(parameter.getType());
    return parameterClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(parameterClass.getQualifiedName());
  }

  private static boolean hasMessage(PsiMethod method) {
    final PsiParameter maybeMessage = method.getParameterList().getParameters()[0];
    final PsiClass maybeString = PsiTypesUtil.getPsiClass(maybeMessage.getType());
    return maybeString != null && CommonClassNames.JAVA_LANG_STRING.equals(maybeString.getQualifiedName());
  }
}
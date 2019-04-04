// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import java.util.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class MigrateAssertToMatcherAssertInspection extends AbstractBaseJavaLocalInspectionTool {

  private final static Logger LOG = Logger.getInstance(MigrateAssertToMatcherAssertInspection.class);
  private final static Map<String, Pair<String, String>> ASSERT_METHODS = new HashMap<>();

  static {
    ASSERT_METHODS.put("assertArrayEquals", Pair.create("$expected$, $actual$", "$actual$, {0}.is($expected$)"));
    ASSERT_METHODS.put("assertEquals", Pair.create("$expected$, $actual$", "$actual$, {0}.is($expected$)"));
    ASSERT_METHODS.put("assertNotEquals", Pair.create("$expected$, $actual$", "$actual$, {0}.not({0}.is($expected$))"));
    ASSERT_METHODS.put("assertSame", Pair.create("$expected$, $actual$", "$actual$, {0}.sameInstance($expected$)"));
    ASSERT_METHODS.put("assertNotSame", Pair.create("$expected$, $actual$", "$actual$, {0}.not({0}.sameInstance($expected$))"));
    ASSERT_METHODS.put("assertNotNull", Pair.create("$obj$", "$obj$, {0}.notNullValue()"));
    ASSERT_METHODS.put("assertNull", Pair.create("$obj$", "$obj$, {0}.nullValue()"));
    ASSERT_METHODS.put("assertTrue", Pair.create("$cond$", "$cond$, {0}.is(true)"));
    ASSERT_METHODS.put("assertFalse", Pair.create("$cond$", "$cond$, {0}.is(false)"));
  }

  private static final String CORE_MATCHERS_CLASS_NAME = "org.hamcrest.CoreMatchers";
  private static final String MATCHERS_CLASS_NAME = "org.hamcrest.Matchers";

  public boolean myStaticallyImportMatchers = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Statically import matcher's methods", this, "myStaticallyImportMatchers");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    GlobalSearchScope resolveScope = holder.getFile().getResolveScope();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(holder.getProject());
    PsiClass coreMatchersClass = javaPsiFacade.findClass(CORE_MATCHERS_CLASS_NAME, resolveScope);
    PsiClass matchersClass = javaPsiFacade.findClass(MATCHERS_CLASS_NAME, resolveScope);
    if (coreMatchersClass == null && matchersClass == null) {
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
          .registerProblem(expression.getMethodExpression(),
                           "Assert expression <code>#ref</code> can be replaced with 'assertThat' call #loc",
                           new MyQuickFix(matchersClass != null ? MATCHERS_CLASS_NAME : CORE_MATCHERS_CLASS_NAME));
      }
    };
  }

  public class MyQuickFix implements LocalQuickFix {
    private static final String ORDERING_COMPARISON_NAME = "org.hamcrest.number.OrderingComparison";
    private final String myMatchersClassName;

    public MyQuickFix(String name) {myMatchersClassName = name;}

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with '" + StringUtil.getShortName(myMatchersClassName) + ".assertThat'";
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
        replaced = TypeConversionDescriptor.replaceExpression(methodCall, templatePair.getFirst(), MessageFormat.format(templatePair.getSecond(), myMatchersClassName));
      }
      catch (IncorrectOperationException e) {
        LOG.error("Replacer can't match expression:\n" +
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
      final String searchTemplate = "'_Assert?." + method.getName() + "(" + (hasMessage ? "$msg$, " : "") + templatePair.getFirst() + ")";
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
          isEqEqForPrimitives ? "{0}.is($right$)" : "{0}.sameInstance($right$)";
        if (JavaTokenType.NE.equals(tokenType)) {
          rightPartOfAfterTemplate = "{0}.not(" + rightPartOfAfterTemplate + ")";
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
      replaceTemplate = ORDERING_COMPARISON_NAME + "." + replaceTemplate;
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
          toRightPart = "{0}.containsString($sub$)";
        } else if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          fromTemplate = "$collection$.contains($element$)";
          toLeftPart = "$collection$, ";
          toRightPart = "{0}.hasItem($element$)";
        }
      }
    }
    else if ("equals".equals(methodName)) {
      final PsiMethod method = expression.resolveMethod();
      if (method != null && isUniqueObjectParameter(method.getParameterList())) {
        fromTemplate = "$left$.equals($right$)";
        toLeftPart = "$left$, ";
        toRightPart = "{0}.is($right$)";
      }
    }
    if (fromTemplate == null) {
      return null;
    }
    if (negate) {
      toRightPart = "{0}.not(" + toRightPart + ")";
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
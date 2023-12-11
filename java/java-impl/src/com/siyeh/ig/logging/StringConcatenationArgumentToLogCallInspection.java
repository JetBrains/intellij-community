// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * @author Bas Leijdekkers
 */
public final class StringConcatenationArgumentToLogCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NonNls
  private static final Set<String> logNames = new HashSet<>();
  static {
    logNames.add("trace");
    logNames.add("debug");
    logNames.add("info");
    logNames.add("warn");
    logNames.add("error");
    logNames.add("fatal");
    logNames.add("log");
  }
  @SuppressWarnings("PublicField") public int warnLevel = 0;

  @Override
  public @NotNull OptPane getOptionsPane() {
    @Nls String[] options = {
      InspectionGadgetsBundle.message("all.levels.option"),
      InspectionGadgetsBundle.message("warn.level.and.lower.option"),
      InspectionGadgetsBundle.message("info.level.and.lower.option"),
      InspectionGadgetsBundle.message("debug.level.and.lower.option"),
      InspectionGadgetsBundle.message("trace.level.option")
    };
    return pane(
      dropdown("warnLevel", InspectionGadgetsBundle.message("warn.on.label"),
                       EntryStream.of(options).mapKeyValue((idx, name) -> option(String.valueOf(idx), name))
                         .toArray(OptDropdown.Option.class))
    );
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.problem.descriptor");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (warnLevel != 0) {
      node.addContent(new Element("option").setAttribute("name", "warnLevel").setAttribute("value", String.valueOf(warnLevel)));
    }
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (!StringConcatenationArgumentToLogCallFix.isAvailable((PsiExpression)infos[0])) {
      return null;
    }
    return new StringConcatenationArgumentToLogCallFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationArgumentToLogCallVisitor();
  }

  private static class StringConcatenationArgumentToLogCallFix extends PsiUpdateModCommandQuickFix {

    StringConcatenationArgumentToLogCallFix() {}

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      @NonNls final StringBuilder newMethodCall = new StringBuilder(methodCallExpression.getMethodExpression().getText());
      newMethodCall.append('(');
      PsiExpression argument = arguments[0];
      int usedArguments;
      if (!(argument instanceof PsiPolyadicExpression)) {
        if (!TypeUtils.expressionHasTypeOrSubtype(argument, "org.slf4j.Marker") || arguments.length < 2) {
          return;
        }
        newMethodCall.append(argument.getText()).append(',');
        argument = arguments[1];
        usedArguments = 2;
        if (!(argument instanceof PsiPolyadicExpression)) {
          return;
        }
      }
      else {
        usedArguments = 1;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)argument;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
      boolean varArgs = false;
      for (PsiMethod otherMethod : methods) {
        if (otherMethod.isVarArgs()) {
          varArgs = true;
          break;
        }
      }
      final List<PsiExpression> newArguments = new ArrayList<>();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean addPlus = false;
      boolean inStringLiteral = false;
      boolean isStringBlock = false;
      StringBuilder logText = new StringBuilder();
      int indent = 0;
      for (PsiExpression operand : operands) {
        if (ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          final String text = operand.getText();
          if (ExpressionUtils.hasStringType(operand) && operand instanceof PsiLiteralExpression literalExpression) {
            final int count = StringUtil.getOccurrenceCount(text, "{}");
            for (int i = 0; i < count && usedArguments + i < arguments.length; i++) {
              newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)arguments[i + usedArguments].copy()));
            }
            usedArguments += count;
            if (!inStringLiteral) {
              if (addPlus) {
                newMethodCall.append('+');
              }
              inStringLiteral = true;
            }
            if (!isStringBlock && literalExpression.isTextBlock()) {
              indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
            }
            isStringBlock = isStringBlock || literalExpression.isTextBlock();
            logText.append(literalExpression.getValue());
          }
          else if (operand instanceof PsiLiteralExpression && PsiTypes.charType().equals(operand.getType()) && inStringLiteral) {
            final Object value = ((PsiLiteralExpression)operand).getValue();
            if (value instanceof Character) {
              logText.append(value);
            }
          }
          else {
            if (inStringLiteral) {
              addLogStrings(newMethodCall, logText, isStringBlock, indent);
              isStringBlock = false;
              inStringLiteral = false;
            }
            if (addPlus) {
              newMethodCall.append('+');
            }
            newMethodCall.append(text);
          }
        }
        else {
          newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)operand.copy()));
          if (!inStringLiteral) {
            if (addPlus) {
              newMethodCall.append('+');
            }
            inStringLiteral = true;
          }
          logText.append("{}");
        }
        addPlus = true;
      }
      while (usedArguments < arguments.length) {
        newArguments.add(arguments[usedArguments++]);
      }
      if (inStringLiteral) {
        addLogStrings(newMethodCall, logText, isStringBlock, indent);
      }
      if (!varArgs && newArguments.size() > 2) {
        newMethodCall.append(", new Object[]{");
        boolean comma = false;
        for (PsiExpression newArgument : newArguments) {
          if (comma) {
            newMethodCall.append(',');
          }
          else {
            comma = true;
          }
          if (newArgument != null) {
            newMethodCall.append(newArgument.getText());
          }
        }
        newMethodCall.append('}');
      }
      else {
        for (PsiExpression newArgument : newArguments) {
          newMethodCall.append(',');
          if (newArgument != null) {
            newMethodCall.append(newArgument.getText());
          }
        }
      }
      newMethodCall.append(')');
      PsiReplacementUtil.replaceExpression(methodCallExpression, newMethodCall.toString());
    }

    private static void addLogStrings(StringBuilder methodCall, StringBuilder logText, boolean isStringBlock, int indent) {
      if (!isStringBlock) {
        methodCall.append('"')
          .append(StringUtil.escapeStringCharacters(logText.toString()))
          .append('"');
        logText.delete(0, logText.length());
        return;
      }
      String delimiters = "\n" + " ".repeat(indent);

      String preparedText = StreamEx.of(logText.toString().split("\n", -1))
        .map(line -> line.endsWith(" ") ? line.substring(0, line.length() - 1) + "\\s" : line)
        .joining(delimiters, delimiters, "");
      preparedText = PsiLiteralUtil.escapeTextBlockCharacters(preparedText, true, true, false);
      methodCall.append("\"\"\"")
        .append(preparedText)
        .append("\"\"\"");
      logText.delete(0, logText.length());
    }

    public static boolean isAvailable(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          return true;
        }
      }
      return false;
    }
  }

  private class StringConcatenationArgumentToLogCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!logNames.contains(referenceName)) {
        return;
      }
      switch (warnLevel) {
        case 4: if ("debug".equals(referenceName)) return;
        case 3: if ("info".equals(referenceName)) return;
        case 2: if ("warn".equals(referenceName)) return;
        case 1: if ("error".equals(referenceName) || "fatal".equals(referenceName)) return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "org.slf4j.Logger") &&
          !InheritanceUtil.isInheritor(containingClass, "org.apache.logging.log4j.Logger") &&
          !InheritanceUtil.isInheritor(containingClass, "org.apache.logging.log4j.LogBuilder")) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression argument = arguments[0];
      if (!ExpressionUtils.hasStringType(argument)) {
        if (arguments.length < 2) {
          return;
        }
        argument = arguments[1];
        if (!ExpressionUtils.hasStringType(argument)) {
          return;
        }
      }
      if (!containsNonConstantConcatenation(argument)) {
        return;
      }
      registerMethodCallError(expression, argument);
    }

    private static boolean containsNonConstantConcatenation(@Nullable PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
        return containsNonConstantConcatenation(parenthesizedExpression.getExpression());
      }
      else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
        if (!ExpressionUtils.hasStringType(polyadicExpression)) {
          return false;
        }
        if (!JavaTokenType.PLUS.equals(polyadicExpression.getOperationTokenType())) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
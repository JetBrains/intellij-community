// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInsight.options.JavaIdentifierValidator;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.format.FormatDecode;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class MalformedFormatStringInspection extends BaseInspection {
  final List<String> classNames;
  final List<String> methodNames;
  /**
   * @noinspection PublicField
   */
  @NonNls public String additionalClasses = "";
  /**
   * @noinspection PublicField
   */
  @NonNls public String additionalMethods = "";

  public MalformedFormatStringInspection() {
    classNames = new ArrayList<>();
    methodNames = new ArrayList<>();
    parseString(additionalClasses, classNames);
    parseString(additionalMethods, methodNames);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("classNames", InspectionGadgetsBundle.message("string.format.class.label"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("string.format.choose.class"))),
      stringList("methodNames", InspectionGadgetsBundle.message("string.format.class.method.label"), new JavaIdentifierValidator())
    );
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(additionalClasses, classNames);
    parseString(additionalMethods, methodNames);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    additionalClasses = formatString(classNames);
    additionalMethods = formatString(methodNames);
    super.writeSettings(node);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object value = infos[0];
    if (value instanceof Exception exception) {
      final String message = exception.getMessage();
      if (message != null) {
        return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.illegal", message);
      }
      return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.malformed");
    }
    final FormatDecode.Validator[] validators = (FormatDecode.Validator[])value;
    final int argumentCount = ((Integer)infos[1]).intValue();
    if (validators.length < argumentCount) {
      return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.many.arguments",
                                             argumentCount, validators.length);
    }
    if (validators.length > argumentCount) {
      final boolean isPrefix = ((Boolean)infos[2]).booleanValue();
      if(isPrefix){
        return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.at.least.too.few.arguments",
                                               argumentCount, validators.length);
      }else{
        return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.few.arguments",
                                               argumentCount, validators.length);
      }
    }
    final PsiType argumentType = (PsiType)infos[2];
    final FormatDecode.Validator validator = (FormatDecode.Validator)infos[3];
    String specifier = validator.getInvalidSpecifier(argumentType);
    return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.arguments.do.not.match.type",
                                           argumentType.getPresentableText(), specifier);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedFormatStringVisitor();
  }

  private class MalformedFormatStringVisitor extends BaseInspectionVisitor {
    @Override
    public void visitCallExpression(@NotNull PsiCallExpression expression) {
      PsiExpressionList list = expression.getArgumentList();
      if (list == null) {
        return;
      }
      FormatDecode.FormatArgument formatArgument = FormatDecode.FormatArgument.extract(expression, methodNames, classNames, true);
      if (formatArgument == null) {
        return;
      }

      int formatArgumentIndex = formatArgument.getIndex();

      PsiExpression[] arguments = list.getExpressions();

      int argumentCount = arguments.length - formatArgumentIndex;

      String value = formatArgument.calculateValue();
      final FormatDecode.Validator[] validators;
      boolean isPrefix = false;
      if (value != null) {
        try {
          validators = FormatDecode.decode(value, argumentCount);
        }
        catch (FormatDecode.IllegalFormatException e) {
          registerError(formatArgument.getExpression(), e);
          return;
        }
      }
      else {
        String prefixValue = formatArgument.calculatePrefixValue();
        isPrefix = true;
        if (prefixValue == null) {
          return;
        }
        try {
          validators = FormatDecode.decodePrefix(prefixValue, argumentCount);
        }
        catch (FormatDecode.IllegalFormatException e) {
          registerError(formatArgument.getExpression(), e);
          return;
        }
      }

      if (argumentCount == 1) {
        final PsiExpression argument = resolveIfPossible(arguments[formatArgumentIndex]);
        final PsiType argumentType = argument.getType();
        if (argumentType instanceof PsiArrayType) {
          final PsiArrayInitializerExpression arrayInitializer;
          if (argument instanceof PsiNewExpression newExpression) {
            arrayInitializer = newExpression.getArrayInitializer();
          }
          else if (argument instanceof PsiArrayInitializerExpression) {
            arrayInitializer = (PsiArrayInitializerExpression)argument;
          }
          else {
            return;
          }
          if (arrayInitializer == null) {
            return;
          }
          arguments = arrayInitializer.getInitializers();
          argumentCount = arguments.length;
          formatArgumentIndex = 0;
        }
      }
      if ((!isPrefix && validators.length != argumentCount) ||
          (isPrefix && validators.length > argumentCount)) {
        if (expression instanceof PsiMethodCallExpression) {
          registerMethodCallError((PsiMethodCallExpression)expression, validators, Integer.valueOf(argumentCount), isPrefix);
        }
        else if (expression instanceof PsiNewExpression) {
          registerNewExpressionError((PsiNewExpression)expression, validators, Integer.valueOf(argumentCount), isPrefix);
        }
        return;
      }
      for (int i = 0; i < validators.length; i++) {
        final FormatDecode.Validator validator = validators[i];
        final PsiExpression argument = arguments[i + formatArgumentIndex];
        final PsiType argumentType = argument.getType();
        if (argumentType == null) {
          continue;
        }
        if (validator != null && !validator.valid(argumentType)) {
          PsiType preciseType = TypeConstraint.fromDfType(CommonDataflow.getDfType(argument)).getPsiType(expression.getProject());
          if (preciseType == null || !validator.valid(preciseType)) {
            registerError(argument, validators, Integer.valueOf(argumentCount), argumentType, validator);
          }
        }
      }
    }

    private static PsiExpression resolveIfPossible(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiVariable variable && target.getContainingFile() == expression.getContainingFile()) {
          final PsiExpression initializer = variable.getInitializer();
          if (initializer != null) {
            return initializer;
          }
        }
      }
      return expression;
    }
  }
}

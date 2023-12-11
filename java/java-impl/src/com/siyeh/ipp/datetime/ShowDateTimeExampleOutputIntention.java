// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.datetime;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * @author Bas Leijdekkers
 */
public final class ShowDateTimeExampleOutputIntention extends PsiBasedModCommandAction<PsiExpression> {
  
  public ShowDateTimeExampleOutputIntention() {
    super(PsiExpression.class);
  }

  private static final CallMatcher DATE_TIME_FORMATTER_METHODS = anyOf(
    staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );
  private static final CallMatcher SIMPLE_DATE_FORMAT_METHODS =
    instanceCall("java.text.SimpleDateFormat", "applyPattern", "applyLocalizedPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING);

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("show.example.date.time.output.intention.family.name");
  }

  enum Formatter {
    NONE, DATE_TIME_FORMATTER, SIMPLE_DATE_FORMAT
  }

  private static @NotNull Formatter getFormatter(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiExpressionList parent)) return Formatter.NONE;
    final PsiType type = expression.getType();
    if (!TypeUtils.isJavaLangString(type)) return Formatter.NONE;
    final PsiElement grandParent = parent.getParent();
    if (grandParent instanceof PsiMethodCallExpression call) {
      if (SIMPLE_DATE_FORMAT_METHODS.test(call)) {
        return Formatter.SIMPLE_DATE_FORMAT;
      }
      if (DATE_TIME_FORMATTER_METHODS.test(call)) {
        return Formatter.DATE_TIME_FORMATTER;
      }
      return Formatter.NONE;
    }
    if (grandParent instanceof PsiNewExpression newExpression) {
      final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
      if (classReference == null || !"SimpleDateFormat".equals(classReference.getReferenceName())) {
        return Formatter.NONE;
      }
      final PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return Formatter.NONE;
      }
      if (!InheritanceUtil.isInheritor(aClass, "java.text.SimpleDateFormat")) {
        return Formatter.NONE;
      }
      return Formatter.SIMPLE_DATE_FORMAT;
    }
    return Formatter.NONE;
  }
  
  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression parent) {
      expression = parent;
    }
    Formatter formatter = getFormatter(expression);
    if (formatter == Formatter.NONE) return null;
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    return value instanceof String ? Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH) : null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression parent) {
      expression = parent;
    }
    Formatter formatter = getFormatter(expression);
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (!(value instanceof String)) return ModCommand.nop();
    return switch (formatter) {
      case NONE -> ModCommand.nop();
      case DATE_TIME_FORMATTER -> {
        try {
          final DateTimeFormatter fmt = DateTimeFormatter.ofPattern((String)value);
          //noinspection HardCodedStringLiteral
          yield ModCommand.info(LocalDateTime.now().format(fmt));
        }
        catch (IllegalArgumentException e) {
          yield ModCommand.error(IntentionPowerPackBundle.message("invalid.pattern.hint.text"));
        }
      }
      case SIMPLE_DATE_FORMAT -> {
        try {
          final SimpleDateFormat format = new SimpleDateFormat((String)value);
          yield ModCommand.info(format.format(new Date()));
        }
        catch (IllegalArgumentException e) {
          yield ModCommand.error(IntentionPowerPackBundle.message("invalid.pattern.hint.text"));
        }
      }
    };
  }
}

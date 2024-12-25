// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public final class ChangeStringLiteralToCharInMethodCallFix extends PsiUpdateModCommandAction<PsiLiteralExpression> {
  private ChangeStringLiteralToCharInMethodCallFix(@NotNull PsiLiteralExpression literal) {
    super(literal);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.single.character.string.to.char.literal.family");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiLiteralExpression literal) {
    final String convertedValue = convertedValue(literal);
    final boolean isString = isString(literal.getType());
    String message = QuickFixBundle.message("fix.single.character.string.to.char.literal.text", literal.getText(),
                                            quote(convertedValue, !isString), isString ? PsiTypes.charType().getCanonicalText() : "String");
    return Presentation.of(message).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiLiteralExpression literal, @NotNull ModPsiUpdater updater) {
    final Object value = literal.getValue();
    if (value != null && value.toString().length() == 1) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
      final PsiExpression newExpression = factory.createExpressionFromText(quote(convertedValue(literal), ! isString(literal.getType())),
                                                                           literal.getParent());
      literal.replace(newExpression);
    }
  }

  private static String quote(final String value, final boolean doubleQuotes) {
    final char quote = doubleQuotes ? '"' : '\'';
    return quote + value + quote;
  }

  private static String convertedValue(@NotNull PsiLiteralExpression literal) {
    String value = String.valueOf(literal.getValue());
    final StringBuilder builder = new StringBuilder();
    StringUtil.escapeStringCharacters(value.length(), value, "\"'", builder);
    return builder.toString();
  }

  public static void registerFixes(final PsiMethod @NotNull [] candidates, final @NotNull PsiConstructorCall call,
                                   final @NotNull HighlightInfo.Builder out, TextRange fixRange) {
    final Set<PsiLiteralExpression> literals = new HashSet<>();
    if (call.getArgumentList() == null) {
      return;
    }
    boolean exactMatch = false;
    for (PsiMethod method : candidates) {
      exactMatch |= findMatchingExpressions(call.getArgumentList().getExpressions(), method, literals);
    }
    if (! exactMatch) {
      processLiterals(literals, out, fixRange);
    }
  }

  public static void registerFixes(final CandidateInfo @NotNull [] candidates,
                                   final @NotNull PsiMethodCallExpression methodCall,
                                   final @Nullable HighlightInfo.Builder info,
                                   @Nullable TextRange fixRange) {
    if (info == null) return;
    final Set<PsiLiteralExpression> literals = new HashSet<>();
    boolean exactMatch = false;
    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo) candidate).getElement();
        exactMatch |= findMatchingExpressions(methodCall.getArgumentList().getExpressions(), method, literals);
      }
    }
    if (!exactMatch) {
      processLiterals(literals, info, fixRange);
    }
  }

  private static void processLiterals(final @NotNull Set<? extends PsiLiteralExpression> literals,
                                      final @NotNull HighlightInfo.Builder info, TextRange fixRange) {
    for (PsiLiteralExpression literal : literals) {
      final ChangeStringLiteralToCharInMethodCallFix fix = new ChangeStringLiteralToCharInMethodCallFix(literal);
      info.registerFix(fix, null, null, fixRange, null);
    }
  }

  /**
   * @return {@code true} if exact TYPEs match
   */
  private static boolean findMatchingExpressions(final PsiExpression[] arguments, final PsiMethod existingMethod,
                                                 final Set<? super PsiLiteralExpression> result) {
    final PsiParameterList parameterList = existingMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();

    if (arguments.length != parameters.length) {
      return false;
    }

    boolean typeMatch = true;
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      final PsiType argumentType = arguments[i].getType();

      typeMatch &= Comparing.equal(parameterType, argumentType);

      PsiLiteralExpression argument = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(arguments[i]), PsiLiteralExpression.class);
      if (argument != null && !result.contains(argument) &&
          (charToString(parameterType, argumentType) || charToString(argumentType, parameterType))) {

        final String value = String.valueOf(argument.getValue());
        if (value != null && value.length() == 1) {
          result.add(argument);
        }
      }
    }
    return typeMatch;
  }

  private static boolean charToString(final PsiType firstType, final PsiType secondType) {
    return Comparing.equal(PsiTypes.charType(), firstType) && isString(secondType);
  }

  private static boolean isString(final PsiType type) {
    return type != null && type.equalsToText(JAVA_LANG_STRING);
  }
}

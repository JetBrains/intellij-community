// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public final class ChangeStringLiteralToCharInMethodCallFix implements IntentionAction, HighPriorityAction {
  private final @NotNull PsiLiteralExpression myLiteral;
  private final @NotNull PsiCall myCall;

  public ChangeStringLiteralToCharInMethodCallFix(@NotNull PsiLiteralExpression literal, @NotNull PsiCall methodCall) {
    myLiteral = literal;
    myCall = methodCall;
  }

  @Override
  @NotNull
  public String getText() {
    final String convertedValue = convertedValue();
    final boolean isString = isString(myLiteral.getType());
    return QuickFixBundle.message("fix.single.character.string.to.char.literal.text", myLiteral.getText(),
                                  quote(convertedValue, ! isString), isString ? PsiType.CHAR.getCanonicalText() : "String");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.single.character.string.to.char.literal.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myCall.isValid() && myLiteral.isValid() && BaseIntentionAction.canModify(myCall);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final Object value = myLiteral.getValue();
    if (value != null && value.toString().length() == 1) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

      final PsiExpression newExpression = factory.createExpressionFromText(quote(convertedValue(), ! isString(myLiteral.getType())),
                                                                           myLiteral.getParent());
      myLiteral.replace(newExpression);
    }
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ChangeStringLiteralToCharInMethodCallFix(PsiTreeUtil.findSameElementInCopy(myLiteral, target),
                                                        PsiTreeUtil.findSameElementInCopy(myCall, target));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static String quote(final String value, final boolean doubleQuotes) {
    final char quote = doubleQuotes ? '"' : '\'';
    return quote + value + quote;
  }

  private String convertedValue() {
    String value = String.valueOf(myLiteral.getValue());
    final StringBuilder builder = new StringBuilder();
    StringUtil.escapeStringCharacters(value.length(), value, "\"'", builder);
    return builder.toString();
  }

  public static void registerFixes(final PsiMethod @NotNull [] candidates, @NotNull final PsiConstructorCall call,
                                   @NotNull final HighlightInfo out, TextRange fixRange) {
    final Set<PsiLiteralExpression> literals = new HashSet<>();
    if (call.getArgumentList() == null) {
      return;
    }
    boolean exactMatch = false;
    for (PsiMethod method : candidates) {
      exactMatch |= findMatchingExpressions(call.getArgumentList().getExpressions(), method, literals);
    }
    if (! exactMatch) {
      processLiterals(literals, call, out, fixRange);
    }
  }

  public static void registerFixes(final CandidateInfo @NotNull [] candidates,
                                   @NotNull final PsiMethodCallExpression methodCall,
                                   @Nullable final HighlightInfo info, 
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
      processLiterals(literals, methodCall, info, fixRange);
    }
  }

  private static void processLiterals(@NotNull final Set<? extends PsiLiteralExpression> literals,
                                      @NotNull final PsiCall call,
                                      @NotNull final HighlightInfo info, TextRange fixRange) {
    for (PsiLiteralExpression literal : literals) {
      final ChangeStringLiteralToCharInMethodCallFix fix = new ChangeStringLiteralToCharInMethodCallFix(literal, call);
      QuickFixAction.registerQuickFixAction(info, fixRange, fix);
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
    return Comparing.equal(PsiType.CHAR, firstType) && isString(secondType);
  }

  private static boolean isString(final PsiType type) {
    return type != null && type.equalsToText(JAVA_LANG_STRING);
  }
}

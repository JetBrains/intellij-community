// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.siyeh.ig.callMatcher.CallMatcher.*;

public final class IncorrectDateTimeFormatInspection extends AbstractBaseJavaLocalInspectionTool {

  private static BiFunction<Character, Integer, String> rangeOf(int from, int to) {
    return (letter, count) -> {
      if (count < from) return InspectionGadgetsBundle.message("inspection.incorrect.date.format.too.few.letters", letter, from, count);
      if (count > to) return InspectionGadgetsBundle.message("inspection.incorrect.date.format.too.many.letters", letter, to, count);
      return null;
    };
  }

  private static BiFunction<Character, Integer, String> setOf(int... numbers) {
    return  (letter, count) -> {
      if (ArrayUtil.indexOf(numbers, count) >= 0) return null;
      final String expected = StringUtil.join(numbers, ", ");
      return InspectionGadgetsBundle.message("inspection.incorrect.date.format.wrong.number.of.letters", letter, expected, count);
    };
  }

  private static final Map<Character, BiFunction<Character, Integer, String>> ALLOWED_DATE_TIME_FORMATTER = Map.ofEntries(
    Map.entry('G', rangeOf(1, 5)),
    Map.entry('u', rangeOf(1, 19)),
    Map.entry('y', rangeOf(1, 19)),
    //according to the specification, must not exceed 19 but current implementation doesn't restrict this
    Map.entry('Y', rangeOf(1, Integer.MAX_VALUE)),
    Map.entry('Q', rangeOf(1, 5)),
    Map.entry('q', rangeOf(1, 5)),
    Map.entry('M', rangeOf(1, 5)),
    Map.entry('L', rangeOf(1, 5)),
    Map.entry('w', rangeOf(1, 2)),
    Map.entry('W', rangeOf(1, 1)),
    Map.entry('d', rangeOf(1, 2)),
    Map.entry('D', rangeOf(1, 3)),
    Map.entry('F', rangeOf(1, 1)),
    Map.entry('g', rangeOf(1, 19)),
    Map.entry('E', rangeOf(1, 5)),
    Map.entry('e', rangeOf(1, 5)),
    Map.entry('c', rangeOf(1, 5)),

    Map.entry('a', rangeOf(1, 1)),
    Map.entry('h', rangeOf(1, 2)),
    Map.entry('H', rangeOf(1, 2)),
    Map.entry('k', rangeOf(1, 2)),
    Map.entry('K', rangeOf(1, 2)),
    Map.entry('m', rangeOf(1, 2)),
    Map.entry('s', rangeOf(1, 2)),
    Map.entry('S', rangeOf(1, 9)), //limit as nanos
    Map.entry('A', rangeOf(1, 19)),
    Map.entry('n', rangeOf(1, 19)),
    Map.entry('N', rangeOf(1, 19)),

    Map.entry('B', setOf(1, 4, 5)),

    Map.entry('V', rangeOf(2, 2)),
    Map.entry('v', setOf(1, 4)),
    Map.entry('z', rangeOf(1, 4)),
    Map.entry('O', setOf(1, 4)),
    Map.entry('X', rangeOf(1, 5)),
    Map.entry('x', rangeOf(1, 5)),
    Map.entry('Z', rangeOf(1, 5))
  );

  private static final CallMatcher PATTERN_METHODS = anyOf(
    staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (PATTERN_METHODS.test(call)) {
          processExpression(call.getArgumentList().getExpressions()[0]);
        }
      }

      private void processExpression(@NotNull PsiExpression expression) {
        Object patternObject = ExpressionUtils.computeConstantExpression(expression);
        if (!(patternObject instanceof String pattern)) return;
        record Token(char character, int pos, int length) {}
        List<Token> tokens = new ArrayList<>();
        int optionalDepth = 0;
        char[] array = pattern.toCharArray();
        boolean inQuote = false;
        int lastQuoteIndex = 0;
        int length = 0;
        for (int pos = 0; pos < array.length; pos++) {
          char c = array[pos];

          //process tokens
          if (!inQuote && isLetter(c)) {
            length++;
            if (pos != array.length - 1 && c == array[pos + 1]) {
              continue;
            }
            else {
              tokens.add(new Token(c, pos - length + 1, length));
              checkPadding(expression, array, pos - length + 1, pos + 1);
            }
          }
          length = 0;


          //check if it starts quote region or one quote
          //case with one quote ('') is processed as quote region
          if (c == '\'') {
            inQuote = !inQuote;
            lastQuoteIndex = pos;
            continue;
          }
          //skip whole region in quote
          if (inQuote) {
            continue;
          }

          checkUnsupportedSymbolsProblem(expression, pos, c);

          if (c == '[') {
            optionalDepth++;
          }
          if (c == ']') {
            optionalDepth--;
            checkOptionalDepthProblem(expression, optionalDepth, pos);
            if (optionalDepth < 0) {
              //reset after problem
              optionalDepth = 0;
            }
          }
        }

        checkInQuoteProblem(expression, inQuote, lastQuoteIndex);

        for (Token token : tokens) {
          checkAvailableCount(expression, token.character, token.pos, token.length);
        }
      }

      private void checkOptionalDepthProblem(@NotNull PsiExpression expression, int optionalDepth, int pos) {
        if (optionalDepth < 0) {
          holder.registerProblem(expression, ExpressionUtils.findStringLiteralRange(expression, pos, pos + 1),
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unpaired", '['));
        }
      }

      private void checkInQuoteProblem(@NotNull PsiExpression expression, boolean inQuote, int pos) {
        if (inQuote) {
          holder.registerProblem(expression, ExpressionUtils.findStringLiteralRange(expression, pos, pos + 1),
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.literal", '\''));
        }
      }

      private void checkUnsupportedSymbolsProblem(@NotNull PsiExpression expression, int pos, char c) {
        if (c == '{' || c == '}' || c == '#') {
          holder.registerProblem(expression, ExpressionUtils.findStringLiteralRange(expression, pos, pos + 1),
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.reserved.character", c));
        }
      }

      private void checkPadding(@NotNull PsiExpression expression, char[] array, int from, int to) {
        if (array[from] == 'p' && (to >= array.length || !isLetter(array[to]))) {
          holder.registerProblem(expression, ExpressionUtils.findStringLiteralRange(expression, from, to),
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.padding", 'p'));
        }
      }

      private static boolean isLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
      }

      private void checkAvailableCount(PsiExpression expression, char character, int pos, int length) {
        if (character == 'p') {
          //checked during tokenizing
          return;
        }
        BiFunction<Character, Integer, @InspectionMessage String> verifier = ALLOWED_DATE_TIME_FORMATTER.get(character);
        if (verifier == null) {
          holder.registerProblem(expression, ExpressionUtils.findStringLiteralRange(expression, pos, pos + length),
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unsupported", character));
        }
        else {
          String message = verifier.apply(character, length);
          if (message != null) {
            final TextRange range = ExpressionUtils.findStringLiteralRange(expression, pos, pos + length);
            holder.registerProblem(expression, range, message);
          }
        }
      }
    };
  }
}

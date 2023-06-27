// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class StringTokenizerDelimiterInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls
  private final static String NEXT_TOKEN = "nextToken";
  @NonNls
  private final static String STRING_TOKENIZER = "java.util.StringTokenizer";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        final PsiMethod method = callExpression.resolveMethod();
        if (method != null && argumentList != null && (method.isConstructor() || NEXT_TOKEN.equals(method.getName()))) {
          final PsiClass stringTokenizer = method.getContainingClass();
          if (stringTokenizer != null && STRING_TOKENIZER.equals(stringTokenizer.getQualifiedName())) {
            final PsiExpression[] arguments = argumentList.getExpressions();
            final int argCount = arguments.length;
            if (method.isConstructor()) {
              if (argCount == 2 || argCount == 3) {
                hasArgumentDuplicates(arguments[1], holder);
              }
            }
            else {
              if (argCount == 1) {
                hasArgumentDuplicates(arguments[0], holder);
              }
            }
          }
        }
      }
    };
  }

  private static void hasArgumentDuplicates(PsiExpression delimiterArgument, ProblemsHolder holder) {
    if (delimiterArgument instanceof PsiLiteralExpression literal && literal.getValue() instanceof String delimiters) {
      final Set<Character> chars = new HashSet<>();
      for (char c : delimiters.toCharArray()) {
        if (!chars.add(c)) {
          holder.problem(delimiterArgument, JavaAnalysisBundle.message("delimiters.argument.contains.duplicated.characters"))
            .fix(new ReplaceDelimitersWithUnique(literal)).register();
          return;
        }
      }
    }
  }

  private final static class ReplaceDelimitersWithUnique extends PsiUpdateModCommandAction<PsiLiteralExpression> {
    ReplaceDelimitersWithUnique(@NotNull PsiLiteralExpression element) {
      super(element);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("replace.stringtokenizer.delimiters.parameter.with.unique.symbols");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiLiteralExpression delimiterArgument, @NotNull ModPsiUpdater updater) {
      final Set<Character> uniqueChars = new LinkedHashSet<>();
      if(!(delimiterArgument.getValue() instanceof String value)) return;
      for (char c : value.toCharArray()) {
        uniqueChars.add(c);
      }
      final String newDelimiters = StringUtil.join(uniqueChars, "");
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.project());
      delimiterArgument.replace(elementFactory.createExpressionFromText('"' + StringUtil.escapeStringCharacters(newDelimiters) + '"', null));
    }
  }
}
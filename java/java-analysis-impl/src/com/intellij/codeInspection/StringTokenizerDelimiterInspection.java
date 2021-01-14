// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
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
      public void visitCallExpression(PsiCallExpression callExpression) {
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
    if (delimiterArgument instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)delimiterArgument).getValue();
      if (value instanceof String) {
        String delimiters = (String)value;
        final Set<Character> chars = new HashSet<>();
        for (char c : delimiters.toCharArray()) {
          if (!chars.add(c)) {
            holder.registerProblem(delimiterArgument, JavaAnalysisBundle.message("delimiters.argument.contains.duplicated.characters"),
                                   new ReplaceDelimitersWithUnique(delimiterArgument));
            return;
          }
        }
      }
    }
  }

  private final static class ReplaceDelimitersWithUnique extends LocalQuickFixOnPsiElement {
    ReplaceDelimitersWithUnique(@NotNull PsiElement element) {
      super(element);
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("replace.stringtokenizer.delimiters.parameter.with.unique.symbols");
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      final Set<Character> uniqueChars = new LinkedHashSet<>();
      final PsiLiteralExpression delimiterArgument = (PsiLiteralExpression)startElement;
      final Object literal = delimiterArgument.getValue();
      if(!(literal instanceof String)) return;
      for (char c : ((String)literal).toCharArray()) {
        uniqueChars.add(c);
      }
      final String newDelimiters = StringUtil.join(uniqueChars, "");
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      delimiterArgument.replace(elementFactory.createExpressionFromText('"' + StringUtil.escapeStringCharacters(newDelimiters) + '"', null));
    }
  }
}
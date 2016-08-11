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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class StringTokenizerDelimiterInspection extends BaseJavaBatchLocalInspectionTool {

  private final static String NEXT_TOKEN = "nextToken";
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
        final Set<Character> chars = new THashSet<>();
        for (char c : delimiters.toCharArray()) {
          if (!chars.add(c)) {
            holder.registerProblem(delimiterArgument, "Delimiters argument contains duplicated characters", new ReplaceDelimitersWithUnique(delimiterArgument));
            return;
          }
        }
      }
    }
  }

  private final static class ReplaceDelimitersWithUnique extends LocalQuickFixOnPsiElement {
    public ReplaceDelimitersWithUnique(@NotNull PsiElement element) {
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
      return "Replace StringTokenizer delimiters parameter with unique symbols";
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      final Set<Character> uniqueChars = new LinkedHashSet<>();
      final PsiLiteralExpression delimiterArgument = (PsiLiteralExpression)startElement;
      for (char c : ((String)delimiterArgument.getValue()).toCharArray()) {
        uniqueChars.add(c);
      }
      final String newDelimiters = StringUtil.join(uniqueChars, "");
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      delimiterArgument.replace(elementFactory.createExpressionFromText(StringUtil.wrapWithDoubleQuote(StringUtil.escaper(true, null).fun(
        newDelimiters)), null));
    }
  }
}
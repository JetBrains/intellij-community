/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExceptionFilter implements Filter, DumbAware {
  private static final String EXCEPTION_IN_THREAD = "Exception in thread \"";
  private static final String CAUSED_BY = "Caused by: ";
  private final ExceptionInfoCache myCache;
  private PsiElementFilter myNextLineRefiner;

  public ExceptionFilter(@NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(scope);
  }

  @Override
  public Result applyFilter(@NotNull final String line, final int textEndOffset) {
    ExceptionWorker worker = new ExceptionWorker(myCache);
    Result result = worker.execute(line, textEndOffset, myNextLineRefiner);
    myNextLineRefiner = result == null ? getRefinerFromException(line) : worker.getLocationRefiner();
    return result;
  }

  private static PsiElementFilter getRefinerFromException(@NotNull String line) {
    String exceptionName = getExceptionFromMessage(line);
    if (exceptionName == null) return null;
    PsiElementFilter exceptionCreationFilter = e -> {
      // We look for new Exception() expression rather than throw statement, because stack-trace is filled in exception constructor
      if (!(e instanceof PsiKeyword) || !(e.textMatches(PsiKeyword.NEW))) return false;
      PsiNewExpression newExpression = ObjectUtils.tryCast(e.getParent(), PsiNewExpression.class);
      if (newExpression == null) return false;
      PsiType type = newExpression.getType();
      return type != null && type.equalsToText(exceptionName);
    };
    PsiElementFilter specificFilter = getExceptionSpecificFilter(exceptionName);
    if (specificFilter == null) return exceptionCreationFilter;
    return element -> exceptionCreationFilter.isAccepted(element) || specificFilter.isAccepted(element);
  }

  @Nullable
  private static PsiElementFilter getExceptionSpecificFilter(String exceptionName) {
    switch (exceptionName) {
      case "java.lang.ArrayIndexOutOfBoundsException":
        return e -> e instanceof PsiJavaToken &&
                    e.textMatches("[") &&
                    e.getParent() instanceof PsiArrayAccessExpression;
      case "java.lang.ArrayStoreException":
        return e -> {
          if (e instanceof PsiJavaToken && e.textMatches("=") && e.getParent() instanceof PsiAssignmentExpression) {
            PsiExpression lExpression = ((PsiAssignmentExpression)e.getParent()).getLExpression();
            return PsiUtil.skipParenthesizedExprDown(lExpression) instanceof PsiArrayAccessExpression;
          }
          return false;
        };
      case "java.lang.AssertionError":
        return e -> e instanceof PsiKeyword && e.textMatches(PsiKeyword.ASSERT);
      case "java.lang.ArithmeticException":
        return e -> {
          if (e instanceof PsiJavaToken && (e.textMatches("%") || e.textMatches("/")) &&
              e.getParent() instanceof PsiPolyadicExpression) {
            PsiExpression prevOperand = PsiTreeUtil.getPrevSiblingOfType(e, PsiExpression.class);
            PsiExpression nextOperand = PsiUtil.skipParenthesizedExprDown(PsiTreeUtil.getNextSiblingOfType(e, PsiExpression.class));
            if (prevOperand != null && TypeConversionUtil.isIntegralNumberType(prevOperand.getType()) &&
                nextOperand != null && TypeConversionUtil.isIntegralNumberType(nextOperand.getType())) {
              while (nextOperand instanceof PsiUnaryExpression && ((PsiUnaryExpression)nextOperand).getOperationTokenType().equals(
                JavaTokenType.MINUS)) {
                nextOperand = PsiUtil.skipParenthesizedExprDown(((PsiUnaryExpression)nextOperand).getOperand());
              }
              if (nextOperand instanceof PsiLiteral) {
                Object value = ((PsiLiteral)nextOperand).getValue();
                if (value instanceof Number && ((Number)value).longValue() != 0) return false;
              }
              return true;
            }
          }
          return false;
        };
      case "java.lang.NegativeArraySizeException":
        return e -> {
          if (e instanceof PsiKeyword && e.textMatches(PsiKeyword.NEW) && e.getParent() instanceof PsiNewExpression) {
            PsiExpression[] dimensions = ((PsiNewExpression)e.getParent()).getArrayDimensions();
            for (PsiExpression dimension : dimensions) {
              if (dimension != null) {
                PsiLiteral literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(dimension), PsiLiteral.class);
                // Explicit negative number like -1 cannot be literal, it's unary expression
                if (literal != null && literal.getValue() instanceof Integer) continue;
              }
              return true;
            }
          }
          return false;
        };
      default:
        return null;
    }
  }

  @Nullable
  private static String getExceptionFromMessage(String line) {
    int firstSpace = line.indexOf(' ');
    if (firstSpace == -1) {
      return getExceptionFromMessage(line, 0, getLength(line));
    }
    if (firstSpace == "Caused".length() && line.startsWith(CAUSED_BY)) {
      int colonPos = line.indexOf(':', CAUSED_BY.length());
      return getExceptionFromMessage(line, CAUSED_BY.length(), colonPos == -1 ? getLength(line) : colonPos);
    }
    if (firstSpace == "Exception".length() && line.startsWith(EXCEPTION_IN_THREAD)) {
      int nextQuotePos = line.indexOf("\" ", EXCEPTION_IN_THREAD.length());
      if (nextQuotePos == -1) return null;
      int start = nextQuotePos + "\" ".length();
      int colonPos = line.indexOf(':', start);
      return getExceptionFromMessage(line, start, colonPos == -1 ? getLength(line) : colonPos);
    }
    if (firstSpace > 2 && line.charAt(firstSpace - 1) == ':') {
      return getExceptionFromMessage(line, 0, firstSpace - 1);
    }
    return null;
  }

  private static int getLength(String line) {
    int length = line.length();
    while (length > 2 && Character.isWhitespace(line.charAt(length - 1))) {
      length--;
    }
    return length;
  }

  /**
   * Returns a substring of {@code line} from {@code from} to {@code to} position after heuristically checking that
   * given substring could be an exception class name. Currently all names which are not very long, consist of 
   * Java identifier symbols and have at least one dot are considered to be possible exception names by this method.
   * 
   * @param line line to extract exception name from
   * @param from start index
   * @param to end index (exclusive)
   * @return a substring between from and to or null if it doesn't look like an exception name.
   */
  private static String getExceptionFromMessage(String line, int from, int to) {
    if (to - from > 200) return null;
    boolean hasDot = false;
    for(int i= from; i<to; i++) {
      char c = line.charAt(i);
      if (c != '.' && !Character.isJavaIdentifierPart(c)) return null;
      hasDot |= c == '.';
    }
    if (!hasDot) return null;
    return line.substring(from, to);
  }
}

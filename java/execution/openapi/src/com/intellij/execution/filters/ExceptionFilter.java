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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExceptionFilter implements Filter, DumbAware {
  private static final String EXCEPTION_IN_THREAD = "Exception in thread \"";
  private static final String CAUSED_BY = "Caused by: ";
  private final ExceptionInfoCache myCache;
  private Predicate<PsiElement> myNextLineRefiner;
  private static final Pattern AIOOBE_MESSAGE = Pattern.compile("(?:Index )?(\\d{1,9})(?: out of bounds for length \\d+)?");
  private static final Pattern CCE_MESSAGE = Pattern.compile("(?:class )?(\\S+) cannot be cast to (?:class )?(\\S+)(?: \\(.+\\))?");

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

  private static Predicate<PsiElement> getRefinerFromException(@NotNull String line) {
    ExceptionInfo exceptionInfo = getExceptionFromMessage(line);
    if (exceptionInfo == null) return null;
    Predicate<PsiElement> exceptionCreationFilter = e -> {
      // We look for new Exception() expression rather than throw statement, because stack-trace is filled in exception constructor
      if (!(e instanceof PsiKeyword) || !(e.textMatches(PsiKeyword.NEW))) return false;
      PsiNewExpression newExpression = ObjectUtils.tryCast(e.getParent(), PsiNewExpression.class);
      if (newExpression == null) return false;
      PsiType type = newExpression.getType();
      return type != null && type.equalsToText(exceptionInfo.myExceptionClassName);
    };
    Predicate<PsiElement> specificFilter = getExceptionSpecificFilter(exceptionInfo);
    if (specificFilter == null) return exceptionCreationFilter;
    return exceptionCreationFilter.or(specificFilter);
  }

  @Nullable
  private static Predicate<PsiElement> getExceptionSpecificFilter(@NotNull ExceptionInfo info) {
    switch (info.myExceptionClassName) {
      case "java.lang.ArrayIndexOutOfBoundsException":
        return e -> isArrayIndexOutOfBoundsSource(info.myExceptionMessage, e);
      case "java.lang.ArrayStoreException":
        return e -> {
          if (e instanceof PsiJavaToken && e.textMatches("=") && e.getParent() instanceof PsiAssignmentExpression) {
            PsiExpression lExpression = ((PsiAssignmentExpression)e.getParent()).getLExpression();
            return PsiUtil.skipParenthesizedExprDown(lExpression) instanceof PsiArrayAccessExpression;
          }
          return false;
        };
      case "java.lang.ClassCastException":
        return getClassCastPredicate(info.myExceptionMessage);
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

  private static Predicate<PsiElement> getClassCastPredicate(@NotNull String message) {
    Matcher matcher = CCE_MESSAGE.matcher(message);
    if (!matcher.matches()) return null;
    String targetClass = matcher.group(2);
    return e -> {
      if (e instanceof PsiJavaToken && e.textMatches("(") && e.getParent() instanceof PsiTypeCastExpression) {
        PsiTypeElement typeElement = ((PsiTypeCastExpression)e.getParent()).getCastType();
        if (typeElement == null) return true;
        return castClassMatches(typeElement.getType(), targetClass);
      }
      if (e instanceof PsiIdentifier && e.getParent() instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)e.getParent();
        PsiElement target = ref.resolve();
        PsiType type;
        if (target instanceof PsiMethod) {
          type = ((PsiMethod)target).getReturnType();
        }
        else if (target instanceof PsiVariable) {
          type = ((PsiVariable)target).getType();
        }
        else {
          return false;
        }
        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (!(psiClass instanceof PsiTypeParameter)) return false;
        // Implicit cast added by compiler
        return castClassMatches(ref.getType(), targetClass);
      }
      return false;
    };
  }

  private static boolean castClassMatches(PsiType type, String className) {
    if (type instanceof PsiPrimitiveType) {
      return className.equals(((PsiPrimitiveType)type).getBoxedTypeName());
    }
    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (castClassMatches(conjunct, className)) return true;
      }
      return false;
    }
    if (type instanceof PsiArrayType) {
      if (className.startsWith("[") && className.length() > 1) {
        PsiType componentType = ((PsiArrayType)type).getComponentType();
        switch (className.charAt(1)) {
          case '[':
            return castClassMatches(componentType, className.substring(1));
          case 'B':
            return componentType.equals(PsiType.BYTE);
          case 'C':
            return componentType.equals(PsiType.CHAR);
          case 'D':
            return componentType.equals(PsiType.DOUBLE);
          case 'F':
            return componentType.equals(PsiType.FLOAT);
          case 'Z':
            return componentType.equals(PsiType.BOOLEAN);
          case 'I':
            return componentType.equals(PsiType.INT);
          case 'J':
            return componentType.equals(PsiType.LONG);
          case 'S':
            return componentType.equals(PsiType.SHORT);
          case 'L':
            if (className.charAt(className.length() - 1) == ';') {
              return castClassMatches(componentType, className.substring(2, className.length() - 1));
            }
            return false;
          default:
            return false;
        }
      }
    }
    if (type instanceof PsiClassType) {
      return classTypeMatches(className, (PsiClassType)type, new THashSet<>());
    }
    return true;
  }

  private static boolean classTypeMatches(String className, PsiClassType classType, Set<PsiClass> visited) {
    PsiClass psiClass = PsiUtil.resolveClassInType(classType);
    if (!visited.add(psiClass)) {
      return true;
    }
    if (psiClass instanceof PsiTypeParameter) {
      for (PsiClassType bound : ((PsiTypeParameter)psiClass).getExtendsList().getReferencedTypes()) {
        if (classTypeMatches(className, bound, visited)) return true;
      }
      return false;
    }
    String name = classType.getClassName();
    if (name == null) return true;
    if (!name.equals(StringUtil.substringAfterLast(className, ".")) &&
        !name.equals(StringUtil.substringAfterLast(className, "$"))) {
      return false;
    }
    if (psiClass != null) {
      if (className.equals(psiClass.getQualifiedName())) return true;
      String packageName = StringUtil.getPackageName(className);
      PsiFile psiFile = psiClass.getContainingFile();
      return psiFile instanceof PsiClassOwner && packageName.equals(((PsiClassOwner)psiFile).getPackageName());
    }
    return true;
  }

  private static boolean isArrayIndexOutOfBoundsSource(@NotNull String message, PsiElement e) {
    if (!(e instanceof PsiJavaToken && e.textMatches("[") && e.getParent() instanceof PsiArrayAccessExpression)) {
      return false;
    }
    Matcher matcher = AIOOBE_MESSAGE.matcher(message);
    if (matcher.matches()) {
      Integer index = Integer.valueOf(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
      PsiLiteralExpression next = ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(e), PsiLiteralExpression.class);
      return next == null || index.equals(next.getValue());
    }
    return true;
  }

  private static class ExceptionInfo {
    final @NotNull String myExceptionClassName;
    final @NotNull String myExceptionMessage;

    private ExceptionInfo(@NotNull String exceptionClassName, @Nullable String exceptionMessage) {
      myExceptionClassName = exceptionClassName;
      myExceptionMessage = StringUtil.notNullize(exceptionMessage);
    }
  }

  @Nullable
  private static ExceptionInfo getExceptionFromMessage(String line) {
    int firstSpace = line.indexOf(' ');
    int colonPos = -1;
    String className = null;
    if (firstSpace == -1) {
      className = getExceptionClassFromMessage(line, 0, getLength(line));
    }
    else if (firstSpace == "Caused".length() && line.startsWith(CAUSED_BY)) {
      colonPos = line.indexOf(':', CAUSED_BY.length());
      className = getExceptionClassFromMessage(line, CAUSED_BY.length(), colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace == "Exception".length() && line.startsWith(EXCEPTION_IN_THREAD)) {
      int nextQuotePos = line.indexOf("\" ", EXCEPTION_IN_THREAD.length());
      if (nextQuotePos == -1) return null;
      int start = nextQuotePos + "\" ".length();
      colonPos = line.indexOf(':', start);
      className = getExceptionClassFromMessage(line, start, colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace > 2 && line.charAt(firstSpace - 1) == ':') {
      colonPos = firstSpace - 1;
      className = getExceptionClassFromMessage(line, 0, firstSpace - 1);
    }
    if (className == null) return null;
    String message = colonPos == -1 ? null : line.substring(colonPos + 1).trim();
    return new ExceptionInfo(className, message);
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
   * @param to   end index (exclusive)
   * @return a substring between from and to or null if it doesn't look like an exception name.
   */
  private static String getExceptionClassFromMessage(String line, int from, int to) {
    if (to - from > 200) return null;
    boolean hasDot = false;
    for (int i = from; i < to; i++) {
      char c = line.charAt(i);
      if (c != '.' && !Character.isJavaIdentifierPart(c)) return null;
      hasDot |= c == '.';
    }
    if (!hasDot) return null;
    return line.substring(from, to);
  }
}

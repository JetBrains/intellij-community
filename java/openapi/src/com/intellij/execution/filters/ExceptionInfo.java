// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ExceptionInfo {
  private static final Pattern AIOOBE_MESSAGE = Pattern.compile("(?:Index )?(\\d{1,9})(?: out of bounds for length \\d+)?");
  private static final Pattern CCE_MESSAGE = Pattern.compile("(?:class )?(\\S+) cannot be cast to (?:class )?(\\S+)(?: \\(.+\\))?");
  // See JEP 358 Helpful NullPointerExceptions for details
  private static final Pattern NPE_MESSAGE = Pattern.compile("Cannot (?:invoke \"(?<invoke>.+)\\(\\)\"|" +
                                                               "assign field \"(?<putfield>.+)\"|" +
                                                               "read field \"(?<getfield>.+)\"|" +
                                                               "store to (?<xastore>[a-z]+) array|" +
                                                               "load from (?<xaload>[a-z]+) array|" +
                                                               "read the array (?<arraylength>length)|" +
                                                               "enter (?<monitor>synchronized) block|" +
                                                               "throw (?<athrow>exception))(?: because .+)?");
  // Methods that could be added by compiler implicitly: either unboxing or getClass (implicit NPE check used for method-refs, etc.)
  private static final Set<String> IGNORED_METHODS = ContainerUtil.immutableSet("intValue", "longValue", "doubleValue", "floatValue",
                                                                                "shortValue", "byteValue", "booleanValue", "charValue",
                                                                                "getClass");
  private static final String EXCEPTION_IN_THREAD = "Exception in thread \"";
  private static final String CAUSED_BY = "Caused by: ";
  private final int myClassNameOffset;
  private final @NotNull String myExceptionClassName;
  private final @NotNull String myExceptionMessage;

  ExceptionInfo(int offset, @NotNull String exceptionClassName, @NotNull String exceptionMessage) {
    myClassNameOffset = offset;
    myExceptionClassName = exceptionClassName;
    myExceptionMessage = exceptionMessage;
  }

  @NotNull ExceptionInfo adjust(@Nullable String message, int offset) {
    message = StringUtil.notNullize(message);
    return new ExceptionInfo(myClassNameOffset + offset, myExceptionClassName, message);
  }

  /**
   * @return a predicate that matches an element within the source line that is likely an exception source
   */
  @NotNull ExceptionLineRefiner getPositionRefiner() {
    Predicate<PsiElement> specificFilter = getExceptionSpecificFilter();
    return new ExceptionLineRefiner() {
      @Override
      public boolean test(PsiElement element) {
        // We look for new Exception() expression rather than throw statement, because stack-trace is filled in exception constructor
        if (element instanceof PsiKeyword && element.textMatches(PsiKeyword.NEW)) {
          PsiNewExpression newExpression = ObjectUtils.tryCast(element.getParent(), PsiNewExpression.class);
          if (newExpression != null) {
            PsiType type = newExpression.getType();
            if (type != null && type.equalsToText(getExceptionClassName())) return true;
          }
        }
        return specificFilter != null && specificFilter.test(element);
      }

      @Override
      public ExceptionInfo getExceptionInfo() {
        return ExceptionInfo.this;
      }
    };
  }

  @Nullable
  private Predicate<PsiElement> getExceptionSpecificFilter() {
    switch (getExceptionClassName()) {
      case "java.lang.ArrayIndexOutOfBoundsException":
        return e -> isArrayIndexOutOfBoundsSource(getExceptionMessage(), e);
      case "java.lang.ArrayStoreException":
        return e -> {
          if (e instanceof PsiJavaToken && e.textMatches("=") && e.getParent() instanceof PsiAssignmentExpression) {
            PsiExpression lExpression = ((PsiAssignmentExpression)e.getParent()).getLExpression();
            return PsiUtil.skipParenthesizedExprDown(lExpression) instanceof PsiArrayAccessExpression;
          }
          return false;
        };
      case "java.lang.ClassCastException":
        return getClassCastPredicate(getExceptionMessage());
      case "java.lang.NullPointerException":
        return getNullPointerPredicate(getExceptionMessage());
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

  /**
   * @return offset of the class name within the parsed line
   */
  public int getClassNameOffset() {
    return myClassNameOffset;
  }

  /**
   * @return exception class name
   */
  public @NotNull String getExceptionClassName() {
    return myExceptionClassName;
  }

  /**
   * @return exception message, empty string if absent
   */
  public @NotNull String getExceptionMessage() {
    return myExceptionMessage;
  }

  private static Predicate<PsiElement> getNullPointerPredicate(String message) {
    if (!message.startsWith("Cannot ")) return null;
    Matcher matcher = NPE_MESSAGE.matcher(message);
    if (!matcher.matches()) return null;
    if (matcher.group("athrow") != null) {
      return e -> e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.THROW_KEYWORD);
    }
    if (matcher.group("monitor") != null) {
      return e -> e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.SYNCHRONIZED_KEYWORD);
    }
    if (matcher.group("arraylength") != null) {
      return e -> {
        PsiExpression qualifier = getFieldReferenceQualifier(e, "length");
        return qualifier != null && qualifier.getType() instanceof PsiArrayType;
      };
    }
    String getField = matcher.group("getfield");
    String putField = matcher.group("putfield");
    String field = getField == null ? putField : getField;
    if (field != null) {
      return e -> {
        PsiExpression qualifier = getFieldReferenceQualifier(e, field);
        return qualifier != null && !(qualifier.getType() instanceof PsiArrayType) &&
               storeMatches(e.getParent(), getField == null);
      };
    }
    boolean arrayLoad = matcher.group("xaload") != null;
    boolean arrayStore = matcher.group("xastore") != null;
    if (arrayLoad || arrayStore) {
      return e -> {
        if (!(e instanceof PsiJavaToken) || !e.textMatches("[")) return false;
        PsiElement parent = e.getParent();
        if (!(parent instanceof PsiArrayAccessExpression)) return false;
        return storeMatches(parent, arrayStore);
      };
    }
    String method = matcher.group("invoke");
    if (method != null) {
      int dotPos = method.lastIndexOf('.');
      if (dotPos != -1) {
        String methodName = method.substring(dotPos + 1);
        if (!IGNORED_METHODS.contains(methodName)) {
          return e -> {
            if (!(e instanceof PsiIdentifier) || !e.textMatches(methodName)) return false;
            PsiElement parent = e.getParent();
            if (!(parent instanceof PsiReferenceExpression)) return false;
            if (!(parent.getParent() instanceof PsiMethodCallExpression)) return false;
            PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)parent).getQualifierExpression());
            if (qualifier == null || qualifier instanceof PsiNewExpression ||
                qualifier instanceof PsiLiteralExpression || qualifier instanceof PsiPolyadicExpression) {
              return false;
            }
            return true;
          };
        }
      }
    }
    return null;
  }

  private static @Nullable PsiExpression getFieldReferenceQualifier(PsiElement e, String fieldName) {
    if (!(e instanceof PsiIdentifier) || !e.textMatches(fieldName)) return null;
    PsiElement parent = e.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return null;
    if (parent.getParent() instanceof PsiMethodCallExpression) return null;
    PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)parent).getQualifierExpression());
    if (qualifier == null || qualifier instanceof PsiNewExpression) return null;
    return qualifier;
  }

  private static boolean storeMatches(PsiElement element, boolean mustBeStore) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      element = parent;
      parent = element.getParent();
    }
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      boolean isStore = assignment.getOperationTokenType().equals(JavaTokenType.EQ) &&
                        assignment.getLExpression() == element;
      return isStore == mustBeStore;
    }
    return !mustBeStore;
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

  @Nullable
  public static ExceptionInfo parseMessage(String line, int textEndOffset) {
    int firstSpace = line.indexOf(' ');
    int colonPos = -1;
    ExceptionInfo info = null;
    if (firstSpace == -1) {
      info = getExceptionClassFromMessage(line, 0, getLength(line));
    }
    else if (firstSpace == "Caused".length() && line.startsWith(CAUSED_BY)) {
      colonPos = line.indexOf(':', CAUSED_BY.length());
      info = getExceptionClassFromMessage(line, CAUSED_BY.length(), colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace == "Exception".length() && line.startsWith(EXCEPTION_IN_THREAD)) {
      int nextQuotePos = line.indexOf("\" ", EXCEPTION_IN_THREAD.length());
      if (nextQuotePos == -1) return null;
      int start = nextQuotePos + "\" ".length();
      colonPos = line.indexOf(':', start);
      info = getExceptionClassFromMessage(line, start, colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace > 2 && line.charAt(firstSpace - 1) == ':') {
      colonPos = firstSpace - 1;
      info = getExceptionClassFromMessage(line, 0, firstSpace - 1);
    }
    else if (firstSpace > 3 && line.charAt(0) == '[' && line.charAt(firstSpace - 1) == ':' && line.charAt(firstSpace - 2) == ']') {
      colonPos = line.indexOf(':', firstSpace);
      info = getExceptionClassFromMessage(line, firstSpace + 1, colonPos == -1 ? getLength(line) : colonPos);
    }
    if (info == null) return null;
    String message = colonPos == -1 ? null : line.substring(colonPos + 1).trim();
    return info.adjust(message, textEndOffset - line.length());
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
  private static ExceptionInfo getExceptionClassFromMessage(String line, int from, int to) {
    if (to - from > 200) return null;
    boolean hasDot = false;
    for (int i = from; i < to; i++) {
      char c = line.charAt(i);
      if (c == '.' && i == from) return null;
      if (c == '$' && !hasDot) return null;
      if (c != '.' && c != '$' && !Character.isJavaIdentifierPart(c)) return null;
      hasDot |= c == '.';
    }
    if (!hasDot) return null;
    return new ExceptionInfo(from, line.substring(from, to), "");
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;

public class NullPointerExceptionInfo extends ExceptionInfo {
  // See JEP 358 Helpful NullPointerExceptions for details
  private static final Pattern NPE_MESSAGE = Pattern.compile("Cannot (?:invoke \"(?<invoke>.+)\\(.*\\)\"|" +
                                                             "assign field \"(?<putfield>.+)\"|" +
                                                             "read field \"(?<getfield>.+)\"|" +
                                                             "store to (?<xastore>[a-z]+) array|" +
                                                             "load from (?<xaload>[a-z]+) array|" +
                                                             "read the array (?<arraylength>length)|" +
                                                             "enter (?<monitor>synchronized) block|" +
                                                             "throw (?<athrow>exception))(?: because .+)?");
  private static final Map<String, PsiPrimitiveType> UNBOXING_METHODS =
    ContainerUtil.<String, PsiPrimitiveType>immutableMapBuilder()
      .put("booleanValue", PsiType.BOOLEAN)
      .put("byteValue", PsiType.BYTE)
      .put("charValue", PsiType.CHAR)
      .put("shortValue", PsiType.SHORT)
      .put("intValue", PsiType.INT)
      .put("longValue", PsiType.LONG)
      .put("floatValue", PsiType.FLOAT)
      .put("doubleValue", PsiType.DOUBLE)
      .build();
  private final @NotNull UnaryOperator<PsiElement> myExtractor;
  
  NullPointerExceptionInfo(int offset, String message) {
    super(offset, CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION, message);
    if (message.isEmpty()) {
      myExtractor = getFallbackExtractor();
    } else {
      UnaryOperator<PsiElement> jep358Predicate = getJep358Extractor(message);
      myExtractor = jep358Predicate == null ? e -> null : jep358Predicate;
    }
  }

  private static UnaryOperator<PsiElement> getFallbackExtractor() {
    return e -> {
      PsiExpression result = fromThrow(e);
      if (result != null) return result;
      result = fromSynchronized(e);
      if (result != null) return result;
      result = matchCompilerGeneratedNullCheck(e);
      if (result != null) return result;
      result = fromUnboxing(e, null);
      if (result != null) return result;
      if (e instanceof PsiJavaToken) {
        IElementType tokenType = ((PsiJavaToken)e).getTokenType();
        if (tokenType.equals(JavaTokenType.DOT)) {
          PsiReferenceExpression ref = tryCast(e.getParent(), PsiReferenceExpression.class);
          if (ref != null) {
            PsiExpression qualifier = ref.getQualifierExpression();
            if (mayBeNull(qualifier)) return qualifier;
          }
        }
        else if (tokenType.equals(JavaTokenType.LBRACKET)) {
          PsiArrayAccessExpression arrayAccess = tryCast(e.getParent(), PsiArrayAccessExpression.class);
          if (arrayAccess != null) {
            PsiExpression arrayExpression = arrayAccess.getArrayExpression();
            if (mayBeNull(arrayExpression)) return arrayExpression;
          }
        }
      }
      return null;
    };
  }
  
  private static PsiExpression fromThrow(PsiElement e) {
    if (e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.THROW_KEYWORD)) {
      PsiThrowStatement throwStatement = tryCast(e.getParent(), PsiThrowStatement.class);
      if (throwStatement != null) {
        return throwStatement.getException();
      }
    }
    return null;
  }

  private static PsiExpression fromUnboxing(PsiElement e, @Nullable PsiPrimitiveType type) {
    PsiExpression boxedExpression = null;
    if (e instanceof PsiIdentifier) {
      PsiReferenceExpression ref = tryCast(e.getParent(), PsiReferenceExpression.class);
      if (ref != null) {
        boxedExpression = ref.getParent() instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)ref.getParent()) : ref;
      }
    }
    if (e instanceof PsiJavaToken) {
      IElementType tokenType = ((PsiJavaToken)e).getTokenType();
      if (tokenType.equals(JavaTokenType.RBRACKET)) {
        PsiArrayAccessExpression arrayAccess = tryCast(e.getParent(), PsiArrayAccessExpression.class);
        if (arrayAccess != null) {
          boxedExpression = arrayAccess;
        }
      }
    }
    if (boxedExpression != null) {
      PsiType expressionType = boxedExpression.getType();
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(expressionType);
      if (unboxedType != null && (type == null || unboxedType.equals(type))) {
        return boxedExpression;
      }
    }
    return null;
  }

  private static PsiExpression fromSynchronized(PsiElement e) {
    if (e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.SYNCHRONIZED_KEYWORD)) {
      PsiSynchronizedStatement synchronizedStatement = tryCast(e.getParent(), PsiSynchronizedStatement.class);
      if (synchronizedStatement != null) {
        return synchronizedStatement.getLockExpression();
      }
    }
    return null;
  }

  private static UnaryOperator<PsiElement> getJep358Extractor(String message) {
    if (!message.startsWith("Cannot ")) return null;
    Matcher matcher = NPE_MESSAGE.matcher(message);
    if (!matcher.matches()) return null;
    if (matcher.group("athrow") != null) {
      return NullPointerExceptionInfo::fromThrow;
    }
    if (matcher.group("monitor") != null) {
      return NullPointerExceptionInfo::fromSynchronized;
    }
    if (matcher.group("arraylength") != null) {
      return e -> {
        PsiExpression qualifier = getFieldReferenceQualifier(e, "length");
        return qualifier != null && qualifier.getType() instanceof PsiArrayType ? qualifier : null;
      };
    }
    String getField = matcher.group("getfield");
    String putField = matcher.group("putfield");
    String field = getField == null ? putField : getField;
    if (field != null) {
      return e -> {
        PsiExpression qualifier = getFieldReferenceQualifier(e, field);
        return qualifier != null && !(qualifier.getType() instanceof PsiArrayType) &&
               storeMatches(e.getParent(), getField == null) ? qualifier : null;
      };
    }
    boolean arrayLoad = matcher.group("xaload") != null;
    boolean arrayStore = matcher.group("xastore") != null;
    if (arrayLoad || arrayStore) {
      return e -> {
        if (!(e instanceof PsiJavaToken) || !e.textMatches("[")) return null;
        PsiElement parent = e.getParent();
        if (!(parent instanceof PsiArrayAccessExpression)) return null;
        return storeMatches(parent, arrayStore) ? ((PsiArrayAccessExpression)parent).getArrayExpression() : null;
      };
    }
    String method = matcher.group("invoke");
    if (method != null) {
      int dotPos = method.lastIndexOf('.');
      if (dotPos != -1) {
        String methodName = method.substring(dotPos + 1);
        PsiPrimitiveType type = UNBOXING_METHODS.get(methodName);
        return e -> {
          if (methodName.equals("getClass") || methodName.equals("ordinal")) {
            // x.getClass() was generated as a null-check by javac until Java 9
            // it's still possible that such a code is executed under Java 14+
            PsiElement result = matchCompilerGeneratedNullCheck(e);
            if (result != null) return result;
          }
          if (type != null) {
            PsiExpression result = fromUnboxing(e, type);
            if (result != null) return result;
          }
          if (!(e instanceof PsiJavaToken) || !((PsiJavaToken)e).getTokenType().equals(JavaTokenType.DOT)) return null;
          PsiElement parent = e.getParent();
          if (!(parent instanceof PsiReferenceExpression)) return null;
          if (!(parent.getParent() instanceof PsiMethodCallExpression)) return null;
          if (!methodName.equals(((PsiReferenceExpression)parent).getReferenceName())) return null;
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)parent).getQualifierExpression());
          return mayBeNull(qualifier) ? qualifier : null;
        };
      }
    }
    return null;
  }

  @Nullable
  static PsiExpression matchCompilerGeneratedNullCheck(PsiElement e) {
    PsiExpression dereferenced = null;
    if (e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType().equals(JavaTokenType.DOUBLE_COLON)) {
      // method reference qualifier
      PsiMethodReferenceExpression methodRef = tryCast(e.getParent(), PsiMethodReferenceExpression.class);
      if (methodRef != null) {
        dereferenced = methodRef.getQualifierExpression();
      }
    }
    else if (e instanceof PsiKeyword && e.textMatches(PsiKeyword.SWITCH)) {
      // switch on string or enum
      PsiSwitchBlock switchBlock = tryCast(e.getParent(), PsiSwitchBlock.class);
      if (switchBlock != null) {
        PsiExpression selector = switchBlock.getExpression();
        if (selector != null) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(selector.getType());
          if (psiClass != null && (psiClass.isEnum() || CommonClassNames.JAVA_LANG_STRING.equals(psiClass.getQualifiedName()))) {
            dereferenced = selector;
          }
        }
      }
    }
    else if (e instanceof PsiKeyword && e.textMatches(PsiKeyword.NEW)) {
      // qualified new
      PsiNewExpression newExpression = tryCast(e.getParent(), PsiNewExpression.class);
      if (newExpression != null) {
        dereferenced = newExpression.getQualifier();
      }
    }
    return mayBeNull(dereferenced) ? dereferenced : null;
  }

  private static boolean mayBeNull(PsiExpression qualifier) {
    return qualifier != null && !(qualifier instanceof PsiNewExpression) &&
           !(qualifier instanceof PsiLiteralExpression) && !(qualifier instanceof PsiPolyadicExpression);
  }

  private static @Nullable PsiExpression getFieldReferenceQualifier(PsiElement e, String fieldName) {
    if (!(e instanceof PsiJavaToken) || !((PsiJavaToken)e).getTokenType().equals(JavaTokenType.DOT)) return null;
    PsiElement parent = e.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return null;
    if (parent.getParent() instanceof PsiMethodCallExpression) return null;
    PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)parent).getQualifierExpression());
    if (!mayBeNull(qualifier)) return null;
    if (!fieldName.equals(((PsiReferenceExpression)parent).getReferenceName())) return null;
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

  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement e) {
    return myExtractor.apply(e);
  }
}

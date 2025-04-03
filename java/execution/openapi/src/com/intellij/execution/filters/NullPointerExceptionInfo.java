// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
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
  private static final Map<String, PsiPrimitiveType> UNBOXING_METHODS = Map.of(
    "booleanValue", PsiTypes.booleanType(),
    "byteValue", PsiTypes.byteType(),
    "charValue", PsiTypes.charType(),
    "shortValue", PsiTypes.shortType(),
    "intValue", PsiTypes.intType(),
    "longValue", PsiTypes.longType(),
    "floatValue", PsiTypes.floatType(),
    "doubleValue", PsiTypes.doubleType());
  private final @NotNull Function<PsiElement, ExceptionLineRefiner.RefinerMatchResult> myExtractor;

  NullPointerExceptionInfo(int offset, String message) {
    super(offset, CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION, message);
    if (message.isEmpty()) {
      myExtractor = getFallbackExtractor();
    }
    else {
      Function<PsiElement, ExceptionLineRefiner.RefinerMatchResult> jep358Predicate = getJep358Extractor(message);
      myExtractor = jep358Predicate == null ? e -> null : jep358Predicate;
    }
  }

  private static Function<PsiElement, ExceptionLineRefiner.RefinerMatchResult> getFallbackExtractor() {
    return e -> {
      ExceptionLineRefiner.RefinerMatchResult matchResult = fromThrow(e);
      if (matchResult != null) return matchResult;
      matchResult = fromSynchronized(e);
      if (matchResult != null) return matchResult;
      matchResult = matchCompilerGeneratedNullCheck(e);
      if (matchResult != null) return matchResult;
      matchResult = fromUnboxing(e, null);
      if (matchResult != null) return matchResult;
      PsiElement nextVisible = PsiTreeUtil.nextVisibleLeaf(e);
      if (nextVisible instanceof PsiJavaToken token && token.getTokenType().equals(JavaTokenType.LBRACKET)) {
        PsiArrayAccessExpression arrayAccess = tryCast(nextVisible.getParent(), PsiArrayAccessExpression.class);
        if (arrayAccess != null) {
          PsiExpression arrayExpression = arrayAccess.getArrayExpression();
          if (mayBeNull(arrayExpression)) return onTheSameLineFor(e, arrayExpression, false);
        }
      }
      PsiElement next = null;
      if (nextVisible instanceof PsiJavaToken token && token.getTokenType().equals((JavaTokenType.DOT))) {
        next = token;
      }
      PsiElement prevVisible = PsiTreeUtil.prevVisibleLeaf(e);
      if (prevVisible instanceof PsiJavaToken token && token.getTokenType().equals((JavaTokenType.DOT))) {
        if (next == null) {
          next = token;
        }
        else {
          next = null;
        }
      }
      if (next != null) {
        PsiReferenceExpression ref = tryCast(next.getParent(), PsiReferenceExpression.class);
        if (ref != null) {
          PsiExpression qualifier = ref.getQualifierExpression();
          if (mayBeNull(qualifier)) return onTheSameLineFor(e, qualifier, false);
        }
      }
      return null;
    };
  }

  private static ExceptionLineRefiner.RefinerMatchResult fromThrow(PsiElement e) {
    if (e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.THROW_KEYWORD)) {
      PsiThrowStatement throwStatement = tryCast(e.getParent(), PsiThrowStatement.class);
      if (throwStatement != null) {
        return onTheSameLineFor(e, throwStatement.getException(), true);
      }
    }
    return null;
  }

  private static ExceptionLineRefiner.RefinerMatchResult fromUnboxing(PsiElement e, @Nullable PsiPrimitiveType type) {
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
        return onTheSameLineFor(e, boxedExpression, false);
      }
    }
    return null;
  }

  private static ExceptionLineRefiner.RefinerMatchResult fromSynchronized(PsiElement e) {
    if (e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(JavaTokenType.SYNCHRONIZED_KEYWORD)) {
      PsiSynchronizedStatement synchronizedStatement = tryCast(e.getParent(), PsiSynchronizedStatement.class);
      if (synchronizedStatement != null) {
        return onTheSameLineFor(e, synchronizedStatement.getLockExpression(), true);
      }
    }
    return null;
  }

  private static Function<PsiElement, ExceptionLineRefiner.RefinerMatchResult> getJep358Extractor(@NonNls String message) {
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
        return qualifier != null && qualifier.getType() instanceof PsiArrayType
               ? onTheSameLineFor(e, qualifier, false)
               : null;
      };
    }
    String getField = matcher.group("getfield");
    String putField = matcher.group("putfield");
    String field = getField == null ? putField : getField;
    if (field != null) {
      return e -> {
        PsiExpression qualifier = getFieldReferenceQualifier(e, field);
        return qualifier != null && !(qualifier.getType() instanceof PsiArrayType) &&
               storeMatches(qualifier.getParent(), getField == null) ? onTheSameLineFor(e, qualifier, false) : null;
      };
    }
    boolean arrayLoad = matcher.group("xaload") != null;
    boolean arrayStore = matcher.group("xastore") != null;
    if (arrayLoad || arrayStore) {
      return current -> {
        PsiElement e = PsiTreeUtil.nextVisibleLeaf(current);
        if (!(e instanceof PsiJavaToken) || !e.textMatches("[")) return null;
        PsiElement parent = e.getParent();
        if (!(parent instanceof PsiArrayAccessExpression)) return null;
        return storeMatches(parent, arrayStore) ?
               onTheSameLineFor(current, ((PsiArrayAccessExpression)parent).getArrayExpression(), false) :
               null;
      };
    }
    String method = matcher.group("invoke");
    if (method != null) {
      int dotPos = method.lastIndexOf('.');
      if (dotPos != -1) {
        @NonNls String methodName = method.substring(dotPos + 1);
        PsiPrimitiveType type = UNBOXING_METHODS.get(methodName);
        return e -> {
          if (methodName.equals("getClass") || methodName.equals("ordinal")) {
            // x.getClass() was generated as a null-check by javac until Java 9
            // it's still possible that such a code is executed under Java 14+
            ExceptionLineRefiner.RefinerMatchResult result = matchCompilerGeneratedNullCheck(e);
            if (result != null) return result;
          }
          if (type != null) {
            ExceptionLineRefiner.RefinerMatchResult result = fromUnboxing(e, type);
            if (result != null) return result;
          }
          //method call
          PsiElement point = PsiTreeUtil.prevVisibleLeaf(e);
          if (!(point instanceof PsiJavaToken token) || !(token.getTokenType().equals(JavaTokenType.DOT))) return null;
          PsiElement parent = point.getParent();
          if (!(parent instanceof PsiReferenceExpression)) return null;
          if (!(parent.getParent() instanceof PsiMethodCallExpression)) return null;
          if (!methodName.equals(((PsiReferenceExpression)parent).getReferenceName())) return null;
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)parent).getQualifierExpression());
          return mayBeNull(qualifier) ? onTheSameLineFor(e, qualifier, false) : null;
        };
      }
    }
    return null;
  }

  public static @Nullable ExceptionLineRefiner.RefinerMatchResult matchCompilerGeneratedNullCheck(PsiElement e) {
    PsiExpression dereferenced = null;
    boolean forward = true;
    if (PsiTreeUtil.nextVisibleLeaf(e) instanceof PsiJavaToken token &&
        token.getTokenType().equals(JavaTokenType.DOUBLE_COLON)) {
      // method reference qualifier
      PsiMethodReferenceExpression methodRef = tryCast(token.getParent(), PsiMethodReferenceExpression.class);
      if (methodRef != null) {
        dereferenced = methodRef.getQualifierExpression();
        forward = false;
      }
    }
    else if (e instanceof PsiJavaToken && e.textMatches("(") &&
             PsiTreeUtil.prevVisibleLeaf(e) instanceof PsiKeyword startSwitch &&
             startSwitch.textMatches(JavaKeywords.SWITCH)) {
      PsiSwitchBlock switchBlock = tryCast(startSwitch.getParent(), PsiSwitchBlock.class);
      if (switchBlock != null) {
        PsiExpression selector = switchBlock.getExpression();
        if (selector != null) {
          PsiType psiType = selector.getType();
          if (!(psiType instanceof PsiPrimitiveType || PsiPrimitiveType.getUnboxedType(psiType) != null)) {
            dereferenced = selector;
          }
        }
      }
    }
    else if (e instanceof PsiIdentifier &&
             PsiTreeUtil.nextVisibleLeaf(e) instanceof PsiJavaToken dot &&
             dot.getTokenType().equals(JavaTokenType.DOT) &&
             PsiTreeUtil.nextVisibleLeaf(dot) instanceof PsiKeyword newKeyWord &&
             newKeyWord.textMatches(JavaKeywords.NEW)) {
      // qualified new
      PsiNewExpression newExpression = tryCast(newKeyWord.getParent(), PsiNewExpression.class);
      if (newExpression != null) {
        dereferenced = newExpression.getQualifier();
      }
    }
    return mayBeNull(dereferenced) ? onTheSameLineFor(e, dereferenced, forward) : null;
  }

  private static boolean mayBeNull(PsiExpression qualifier) {
    return qualifier != null && !(qualifier instanceof PsiNewExpression) &&
           !(qualifier instanceof PsiLiteralExpression) && !(qualifier instanceof PsiPolyadicExpression);
  }

  private static @Nullable PsiExpression getFieldReferenceQualifier(PsiElement current, String fieldName) {
    PsiElement e = PsiTreeUtil.nextVisibleLeaf(current);
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
    if (parent instanceof PsiAssignmentExpression assignment) {
      boolean isStore = assignment.getOperationTokenType().equals(JavaTokenType.EQ) &&
                        assignment.getLExpression() == element;
      return isStore == mustBeStore;
    }
    return !mustBeStore;
  }

  @Override
  ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement e) {
    return myExtractor.apply(e);
  }
}

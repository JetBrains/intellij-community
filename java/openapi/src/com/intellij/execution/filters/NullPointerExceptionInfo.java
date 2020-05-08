// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NullPointerExceptionInfo extends ExceptionInfo {
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
  final Predicate<PsiElement> myPredicate;
  
  NullPointerExceptionInfo(int offset, String message) {
    super(offset, CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION, message);
    myPredicate = getPredicate(message);
  }

  private static Predicate<PsiElement> getPredicate(String message) {
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

  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement e) {
    return (myPredicate != null && myPredicate.test(e)) ? e : null;
  }
}

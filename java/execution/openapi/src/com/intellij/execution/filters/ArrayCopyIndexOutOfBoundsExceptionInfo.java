// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArrayCopyIndexOutOfBoundsExceptionInfo extends ExceptionInfo {
  private static final @NonNls String SOURCE_INDEX_PREFIX = "source index ";
  private static final @NonNls String DESTINATION_INDEX_PREFIX = "destination index ";
  private static final @NonNls String COMMON_PREFIX = "arraycopy: ";
  private static final @NonNls String LENGTH_PREFIX = "length ";
  private final int myValue;
  private final int myParameter;

  public ArrayCopyIndexOutOfBoundsExceptionInfo(int offset, String message, int value, int parameter) {
    super(offset, "java.lang.ArrayIndexOutOfBoundsException", message);
    myValue = value;
    myParameter = parameter;
  }

  public int getValue() {
    return myValue;
  }

  @Override
  @Nullable ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement current) {
    if(!(current instanceof PsiJavaToken && current.textMatches("("))) return null;
    PsiElement prevElement = PsiTreeUtil.prevVisibleLeaf(current);
    if (!(prevElement instanceof PsiIdentifier)) return null;
    if (!prevElement.textMatches("arraycopy")) return null;
    PsiElement ref = prevElement.getParent();
    if (!(ref instanceof PsiReferenceExpression)) return null;
    PsiMethodCallExpression call = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 5) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !"java.lang.System".equals(containingClass.getQualifiedName())) return null;
    return onTheSameLineFor(current, args[myParameter], true);
  }

  public static ExceptionInfo tryCreate(int offset, String message) {
    if (!message.startsWith(COMMON_PREFIX)) return null;
    int parameter;
    int indexOffset = COMMON_PREFIX.length();
    if (message.startsWith(SOURCE_INDEX_PREFIX, COMMON_PREFIX.length())) {
      parameter = 1;
      indexOffset += SOURCE_INDEX_PREFIX.length();
    }
    else if (message.startsWith(DESTINATION_INDEX_PREFIX, COMMON_PREFIX.length())) {
      parameter = 3;
      indexOffset += DESTINATION_INDEX_PREFIX.length();
    }
    else if (message.startsWith(LENGTH_PREFIX, COMMON_PREFIX.length())) {
      parameter = 4;
      indexOffset += LENGTH_PREFIX.length();
    } else {
      return null;
    }
    int indexEndOffset = message.indexOf(' ', indexOffset);
    if (indexEndOffset == -1) return null;
    String indexStr = message.substring(indexOffset, indexEndOffset);
    int index;
    try {
      index = Integer.parseInt(indexStr);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    return new ArrayCopyIndexOutOfBoundsExceptionInfo(offset, message, index, parameter);
  }
}

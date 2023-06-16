// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArrayIndexOutOfBoundsExceptionInfo extends ExceptionInfo {
  private static final Pattern AIOOBE_MESSAGE = Pattern.compile("(?:Index )?(-?\\d{1,9})(?: out of bounds for length \\d+)?");
  
  private final @Nullable Integer myIndex;

  ArrayIndexOutOfBoundsExceptionInfo(int offset, String message) {
    super(offset, "java.lang.ArrayIndexOutOfBoundsException", message);
    Matcher matcher = AIOOBE_MESSAGE.matcher(message);
    if (matcher.matches()) {
      myIndex = Integer.valueOf(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
    } else {
      myIndex = null;
    }
  }

  @Override
  public ExceptionInfo consumeStackLine(String line) {
    if (line.contains("java.lang.System.arraycopy")) {
      return ArrayCopyIndexOutOfBoundsExceptionInfo.tryCreate(getClassNameOffset(), getExceptionMessage());
    }
    return super.consumeStackLine(line);
  }

  public @Nullable Integer getIndex() {
    return myIndex;
  }

  @Override
  ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement e) {
    if (!(e instanceof PsiJavaToken && e.textMatches("[") && e.getParent() instanceof PsiArrayAccessExpression)) {
      return null;
    }
    PsiExpression indexExpression = ((PsiArrayAccessExpression)e.getParent()).getIndexExpression();
    if (indexExpression == null) return null;
    if (myIndex != null) {
      Object value = ConstantExpressionUtil.computeCastTo(indexExpression, PsiTypes.intType());
      if (value != null && !value.equals(myIndex)) return null;
    }
    //there is no reason to try to find more certain place because it doesn't have its own record in LineNumberTable
    return ExceptionLineRefiner.RefinerMatchResult.of(indexExpression);
  }
}

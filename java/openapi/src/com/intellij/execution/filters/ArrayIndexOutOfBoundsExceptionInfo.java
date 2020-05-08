// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
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

  public @Nullable Integer getIndex() {
    return myIndex;
  }

  @Override
  boolean isSpecificExceptionElement(PsiElement e) {
    if (!(e instanceof PsiJavaToken && e.textMatches("[") && e.getParent() instanceof PsiArrayAccessExpression)) {
      return false;
    }
    if (myIndex != null) {
      PsiLiteralExpression next = ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(e), PsiLiteralExpression.class);
      return next == null || myIndex.equals(next.getValue());
    }
    return true;
  }
}

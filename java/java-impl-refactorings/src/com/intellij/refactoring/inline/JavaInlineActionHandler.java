// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.xml.util.JspFileTypeUtil;


public abstract class JavaInlineActionHandler extends InlineActionHandler {
  @Override
  public boolean isEnabledForLanguage(Language l) {
    return isJavaLanguage(l);
  }

  protected static boolean isJavaLanguage(Language l) {
    return l instanceof JavaLanguage ||
           JspFileTypeUtil.isJspOrJspX(l);
  }
}

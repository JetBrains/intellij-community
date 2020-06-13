// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.codeInsight.CodeInsightActionHandler;

public final class CodeInsightActions {
  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    IMPLEMENT_METHOD = new LanguageExtension<>("com.intellij.codeInsight.implementMethod");

  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    OVERRIDE_METHOD = new LanguageExtension<>("com.intellij.codeInsight.overrideMethod");

  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    DELEGATE_METHODS = new LanguageExtension<>("com.intellij.codeInsight.delegateMethods");

  public static final LanguageExtension<CodeInsightActionHandler>
    GOTO_SUPER = new LanguageExtension<>("com.intellij.codeInsight.gotoSuper");

  private CodeInsightActions() {
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.util;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;

public final class CompletionStyleUtil {
  public static CommonCodeStyleSettings getCodeStyleSettings(InsertionContext context) {
    Language lang = PsiUtilCore.getLanguageAtOffset(context.getFile(), context.getTailOffset());
    return CodeStyle.getLanguageSettings(context.getFile(), lang);
  }
}

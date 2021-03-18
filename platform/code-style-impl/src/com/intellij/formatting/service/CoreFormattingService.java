// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

public final class CoreFormattingService implements FormattingService {
  private final static Logger LOG =Logger.getInstance(CoreFormattingService.class);

  @Override
  public boolean canFormat(@NotNull PsiFile file) {
    return true;
  }

}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class HighlightInfoFilterImpl implements HighlightInfoFilter {
  private static final class Holder {
    private static final boolean ourTestMode = ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public boolean accept(@NotNull HighlightInfo info, PsiFile file) {
    if (file != null && file.getOriginalFile() instanceof PsiCompiledFile) {
      return info.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY;
    }
    if (info.findRegisteredQuickFix((__, __1) -> true) != null) {
      return true; // must not hide if there are fixes to show
    }
    if (Holder.ourTestMode) {
      return true; // Tests need to verify highlighting is applied no matter what attributes are defined for this kind of highlighting
    }

    TextAttributes attributes = info.getTextAttributes(file, null);
    // optimization
    return attributes == TextAttributes.ERASE_MARKER ||
           attributes != null &&
           !(attributes.isEmpty() && info.getSeverity() == HighlightSeverity.INFORMATION && info.getGutterIconRenderer() == null && info.getToolTip() == null);
  }
}

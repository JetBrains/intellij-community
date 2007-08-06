package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HighlightInfoFilterImpl implements HighlightInfoFilter {
  private final static boolean ourTestMode = ApplicationManager.getApplication().isUnitTestMode();

  public boolean accept(@NotNull HighlightInfo info, PsiFile file) {
    if (ourTestMode) return true; // Tests need to verify highlighting is applied no matter what attributes are defined for this kind of highlighting

    TextAttributes attributes = info.getTextAttributes(file);
    // optimization
     return attributes == TextAttributes.ERASE_MARKER || attributes != null &&
           !(attributes.isEmpty() && info.getSeverity() == HighlightSeverity.INFORMATION && info.getGutterIconRenderer() == null);
  }
}

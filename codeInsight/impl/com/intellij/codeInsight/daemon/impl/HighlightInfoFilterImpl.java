package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HighlightInfoFilterImpl implements HighlightInfoFilter, ApplicationComponent {
  public boolean accept(HighlightInfo info, PsiFile file) {
    TextAttributes attributes = info.getTextAttributes();
    // optimization
     return attributes == TextAttributes.ERASE_MARKER || attributes != null &&
           !(attributes.isEmpty() && info.getSeverity() == HighlightSeverity.INFORMATION && info.getGutterIconRenderer() == null);
  }

  @NotNull
  public String getComponentName() {
    return "HighlightInfoFilter";
  }

  public void initComponent() { }
  public void disposeComponent() { }
}

/*
 * User: anna
 * Date: 17-Jun-2009
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;

import java.awt.*;
import java.util.List;

public abstract class SeveritiesProvider {
  public static final ExtensionPointName<SeveritiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.severitiesProvider");
  
  /**
   * @see TextAttributesKey#createTextAttributesKey(String, com.intellij.openapi.editor.markup.TextAttributes)
   */
  public abstract List<HighlightInfoType> getSeveritiesHighlightInfoTypes();

  public boolean isGotoBySeverityEnabled(HighlightSeverity minSeverity) {
    return true;
  }

  public Color getTrafficRendererColor(TextAttributes textAttributes) {
    return textAttributes.getErrorStripeColor();
  }
}
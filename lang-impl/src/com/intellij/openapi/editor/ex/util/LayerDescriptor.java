package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;

/**
 * @author max
 */
public class LayerDescriptor {
  private SyntaxHighlighter myLayerHighlighter;
  private String myTokenSeparator;
  private TextAttributesKey myBackground;

  public LayerDescriptor(final SyntaxHighlighter layerHighlighter, final String tokenSeparator, final TextAttributesKey background) {
    myBackground = background;
    myLayerHighlighter = layerHighlighter;
    myTokenSeparator = tokenSeparator;
  }
  public LayerDescriptor(final SyntaxHighlighter layerHighlighter, final String tokenSeparator) {
    this(layerHighlighter, tokenSeparator, null);
  }

  public SyntaxHighlighter getLayerHighlighter() {
    return myLayerHighlighter;
  }

  public String getTokenSeparator() {
    return myTokenSeparator;
  }

  public TextAttributesKey getBackgroundKey() {
    return myBackground;
  }
}

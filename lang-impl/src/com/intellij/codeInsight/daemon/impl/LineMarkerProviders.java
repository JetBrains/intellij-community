package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LineMarkerProviders extends LanguageExtension<LineMarkerProvider> {
  public static LineMarkerProviders INSTANCE = new LineMarkerProviders();

  private LineMarkerProviders() {
    super("com.intellij.codeInsight.lineMarkerProvider");
  }
}

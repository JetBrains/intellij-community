package com.intellij.lang.parameterInfo;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LanguageParameterInfo extends LanguageExtension<ParameterInfoHandler> {
  public static final LanguageParameterInfo INSTANCE = new LanguageParameterInfo();

  private LanguageParameterInfo() {
    super("com.intellij.codeInsight.parameterInfo");
  }
}
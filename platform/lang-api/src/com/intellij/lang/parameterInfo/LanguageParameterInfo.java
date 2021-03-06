// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.parameterInfo;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public final class LanguageParameterInfo extends LanguageExtension<ParameterInfoHandler> {
  public static final LanguageParameterInfo INSTANCE = new LanguageParameterInfo();

  private LanguageParameterInfo() {
    super("com.intellij.codeInsight.parameterInfo");
  }
}
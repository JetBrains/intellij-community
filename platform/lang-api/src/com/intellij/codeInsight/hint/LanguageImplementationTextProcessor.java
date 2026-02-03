// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import com.intellij.lang.LanguageExtension;

public class LanguageImplementationTextProcessor extends LanguageExtension<ImplementationTextProcessor>{
  public static final LanguageImplementationTextProcessor INSTANCE = new LanguageImplementationTextProcessor();

  public LanguageImplementationTextProcessor() {
    super("com.intellij.lang.implementationTextProcessor", null);
  }
}
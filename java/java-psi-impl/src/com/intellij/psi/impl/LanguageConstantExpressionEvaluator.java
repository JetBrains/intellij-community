// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.LanguageExtension;

/**
 * @author Serega.Vasiliev
 */
public final class LanguageConstantExpressionEvaluator extends LanguageExtension<ConstantExpressionEvaluator> {
  public static final LanguageConstantExpressionEvaluator INSTANCE = new LanguageConstantExpressionEvaluator();

  private LanguageConstantExpressionEvaluator() {
    super("com.intellij.constantExpressionEvaluator");
  }
}


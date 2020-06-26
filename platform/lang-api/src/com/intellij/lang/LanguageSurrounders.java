// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.surroundWith.SurroundDescriptor;

public final class LanguageSurrounders extends LanguageExtension<SurroundDescriptor> {
  public static final LanguageSurrounders INSTANCE = new LanguageSurrounders();

  private LanguageSurrounders() {
    super("com.intellij.lang.surroundDescriptor");
  }
}
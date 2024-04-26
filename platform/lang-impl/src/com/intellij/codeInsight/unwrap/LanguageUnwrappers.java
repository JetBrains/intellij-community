// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.unwrap;

import com.intellij.lang.LanguageExtension;

public final class LanguageUnwrappers extends LanguageExtension<UnwrapDescriptor>{
  public static final LanguageUnwrappers INSTANCE = new LanguageUnwrappers();

  public LanguageUnwrappers() {
    super("com.intellij.lang.unwrapDescriptor");
  }
}

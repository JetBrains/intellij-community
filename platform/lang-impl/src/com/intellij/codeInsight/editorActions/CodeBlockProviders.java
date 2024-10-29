// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class CodeBlockProviders extends LanguageExtension<CodeBlockProvider> {
  public static final CodeBlockProviders INSTANCE = new CodeBlockProviders();

  private CodeBlockProviders() {
    super("com.intellij.codeBlockProvider");
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.emacs;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LanguageEmacsExtension extends LanguageExtension<EmacsProcessingHandler> {
  public static final LanguageEmacsExtension INSTANCE = new LanguageEmacsExtension();

  public LanguageEmacsExtension() {
    super("com.intellij.lang.emacs", new DefaultEmacsProcessingHandler());
  }
}

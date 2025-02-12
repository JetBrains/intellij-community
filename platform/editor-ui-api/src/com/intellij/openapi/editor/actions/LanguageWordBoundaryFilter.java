// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.lang.LanguageExtension;

public final class LanguageWordBoundaryFilter extends LanguageExtension<WordBoundaryFilter> {

  /**
   * @deprecated use {@link LanguageWordBoundaryFilter#getInstance()}
   */
  @Deprecated // external usage
  public static final LanguageWordBoundaryFilter INSTANCE = new LanguageWordBoundaryFilter();

  public static LanguageWordBoundaryFilter getInstance() {
    return INSTANCE;
  }

  private LanguageWordBoundaryFilter() {
    super("com.intellij.wordBoundaryFilter", new WordBoundaryFilter());
  }
}

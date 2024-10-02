// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.fillParagraph;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LanguageFillParagraphExtension extends LanguageExtension<ParagraphFillHandler> {
  public static final LanguageFillParagraphExtension INSTANCE = new LanguageFillParagraphExtension();

  public LanguageFillParagraphExtension() {
    super("com.intellij.codeInsight.fillParagraph", new ParagraphFillHandler());
  }
}

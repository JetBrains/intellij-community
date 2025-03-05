// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.lexer.FlexAdapter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class TemplateTextLexer extends FlexAdapter {
  public TemplateTextLexer() {
    super(new _TemplateTextLexer());
  }
}

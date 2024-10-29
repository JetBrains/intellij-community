// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.lexer.FlexAdapter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class MacroLexer extends FlexAdapter {
  public MacroLexer() {
    super(new _MacroLexer());
  }
}

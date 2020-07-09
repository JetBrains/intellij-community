// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.lexer.FlexAdapter;

/**
 * @author yole
 */
public class MacroLexer extends FlexAdapter {
  public MacroLexer() {
    super(new _MacroLexer());
  }
}

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

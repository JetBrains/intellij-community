package com.intellij.codeInsight.template.impl;

import com.intellij.lexer.FlexAdapter;

class TemplateTextLexer extends FlexAdapter {
  public TemplateTextLexer() {
    super(new _TemplateTextLexer());
  }
}

package com.intellij.codeInsight.template.impl;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;

interface TemplateTokenType {
  IElementType TEXT = new IElementType("TEXT", Language.ANY);
  IElementType VARIABLE = new IElementType("VARIABLE", Language.ANY);
  IElementType ESCAPE_DOLLAR = new IElementType("ESCAPE_DOLLAR", Language.ANY);
}
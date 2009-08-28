package com.intellij.ide.fileTemplates.impl;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;

interface FileTemplateTokenType {
  IElementType TEXT = new IElementType("TEXT", Language.ANY);
  IElementType MACRO = new IElementType("MACRO", Language.ANY);
  IElementType DIRECTIVE = new IElementType("DIRECTIVE", Language.ANY);
}
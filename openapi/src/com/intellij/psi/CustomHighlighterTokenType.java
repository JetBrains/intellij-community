/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;


/**
 * @author Yura Cangea
 * @version 1.0
 */
public interface CustomHighlighterTokenType {
  class CustomElementType extends IElementType {
    public CustomElementType(String debugName) {
      super(debugName);
    }
  }
  
  IElementType KEYWORD_1 = new CustomElementType("KEYWORD_1");
  IElementType KEYWORD_2 = new CustomElementType("KEYWORD_2");
  IElementType KEYWORD_3 = new CustomElementType("KEYWORD_3");
  IElementType KEYWORD_4 = new CustomElementType("KEYWORD_4");

  int KEYWORD_TYPE_COUNT = 4;

  IElementType STRING = new CustomElementType("STRING");
  IElementType NUMBER = new CustomElementType("NUMBER");
  IElementType IDENTIFIER = new CustomElementType("IDENTIFIER");
  IElementType LINE_COMMENT = new CustomElementType("LINE_COMMENT");
  IElementType MULTI_LINE_COMMENT = new CustomElementType("MULTI_LINE_COMMENT");
  IElementType WHITESPACE = new CustomElementType("WHITESPACE");
  IElementType CHARACTER = new CustomElementType("CHARACTER");

  IElementType L_BRACE = new CustomElementType("L_BRACE");
  IElementType R_BRACE = new CustomElementType("R_BRACE");
  IElementType L_BRACKET = new CustomElementType("L_BRACKET");
  IElementType R_BRACKET = new CustomElementType("R_BRACKET");
  IElementType L_PARENTH = new CustomElementType("L_PARENTH");
  IElementType R_PARENTH = new CustomElementType("R_PARENTH");
}

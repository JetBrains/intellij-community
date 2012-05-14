/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;


/**
 * @author Yura Cangea
 * @version 1.0
 */
public interface CustomHighlighterTokenType {
  class CustomElementType extends IElementType {
    public CustomElementType(@NonNls String debugName) {
      super(debugName, null);
    }
  }

  IElementType KEYWORD_1 = new CustomElementType("KEYWORD_1");
  IElementType KEYWORD_2 = new CustomElementType("KEYWORD_2");
  IElementType KEYWORD_3 = new CustomElementType("KEYWORD_3");
  IElementType KEYWORD_4 = new CustomElementType("KEYWORD_4");

  int KEYWORD_TYPE_COUNT = 4;

  IElementType STRING = new CustomElementType("STRING");
  IElementType SINGLE_QUOTED_STRING = new CustomElementType("SINGLE_QUOTED_STRING");
  IElementType NUMBER = new CustomElementType("NUMBER");
  IElementType IDENTIFIER = new CustomElementType("IDENTIFIER");
  IElementType LINE_COMMENT = new CustomElementType("LINE_COMMENT");
  IElementType MULTI_LINE_COMMENT = new CustomElementType("MULTI_LINE_COMMENT");
  IElementType WHITESPACE = new CustomElementType("WHITESPACE");
  IElementType CHARACTER = new CustomElementType("CHARACTER");
  IElementType PUNCTUATION = new CustomElementType("PUNCTUATION");

  IElementType L_BRACE = new CustomElementType("L_BRACE");
  IElementType R_BRACE = new CustomElementType("R_BRACE");
  IElementType L_ANGLE = new CustomElementType("L_BROCKET");
  IElementType R_ANGLE = new CustomElementType("R_BROCKET");
  IElementType L_BRACKET = new CustomElementType("L_BRACKET");
  IElementType R_BRACKET = new CustomElementType("R_BRACKET");
  IElementType L_PARENTH = new CustomElementType("L_PARENTH");
  IElementType R_PARENTH = new CustomElementType("R_PARENTH");
  IElementType CUSTOM_CONTENT = new CustomElementType("CUSTOM_CONTENT");
}

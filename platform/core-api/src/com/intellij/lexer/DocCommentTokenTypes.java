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

package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Allows to specify a set of language-dependent token types for the doc comment lexer.
 *
 * @author yole
 */
public interface DocCommentTokenTypes {
  IElementType commentStart();
  IElementType commentEnd();
  IElementType commentData();
  TokenSet spaceCommentsTokenSet();
  IElementType space();
  IElementType tagValueToken();
  IElementType tagValueLParen();
  IElementType tagValueRParen();
  IElementType tagValueSharp();
  IElementType tagValueComma();
  IElementType tagName();
  IElementType tagValueLT();
  IElementType tagValueGT();
  IElementType inlineTagStart();
  IElementType inlineTagEnd();
  IElementType badCharacter();
  IElementType commentLeadingAsterisks();
}

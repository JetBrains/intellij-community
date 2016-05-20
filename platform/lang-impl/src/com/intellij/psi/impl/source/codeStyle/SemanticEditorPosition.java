/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
public abstract class SemanticEditorPosition {
  public interface SyntaxElement {}
  
  private final HighlighterIterator myIterator;
  private final CharSequence myChars;

  public SemanticEditorPosition(@NotNull EditorEx editor, int offset) {
    myChars = editor.getDocument().getCharsSequence();
    myIterator = editor.getHighlighter().createIterator(offset);
  }
  
  public SemanticEditorPosition beforeOptional(@NotNull SyntaxElement syntaxElement) {
    if (!myIterator.atEnd()) {
      if (syntaxElement.equals(map(myIterator.getTokenType()))) myIterator.retreat();
    }
    return this;
  }
  
  public boolean isAtMultiline() {
    if (!myIterator.atEnd()) {
      return CharArrayUtil.containLineBreaks(myChars, myIterator.getStart(), myIterator.getEnd());
    }
    return false;
  }
  
  public SemanticEditorPosition before() {
    if (!myIterator.atEnd()) {
      myIterator.retreat();
    }
    return this;
  }
  
  public boolean isAt(@NotNull SyntaxElement syntaxElement) {
    return !myIterator.atEnd() && syntaxElement.equals(map(myIterator.getTokenType()));
  }
  
  public abstract SyntaxElement map(@NotNull IElementType elementType); 
}

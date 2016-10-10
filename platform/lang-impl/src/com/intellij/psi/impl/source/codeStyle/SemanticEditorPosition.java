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

import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public abstract class SemanticEditorPosition {
  public interface SyntaxElement {}
  
  private final EditorEx myEditor;
  private final HighlighterIterator myIterator;
  private final CharSequence myChars;

  public SemanticEditorPosition(@NotNull EditorEx editor, int offset) {
    myEditor = editor;
    myChars = editor.getDocument().getCharsSequence();
    myIterator = editor.getHighlighter().createIterator(offset);
  }
  
  public SemanticEditorPosition beforeOptional(@NotNull SyntaxElement syntaxElement) {
    if (!myIterator.atEnd()) {
      if (syntaxElement.equals(map(myIterator.getTokenType()))) myIterator.retreat();
    }
    return this;
  }
  
  public SemanticEditorPosition beforeOptionalMix(@NotNull SyntaxElement... elements) {
    while (isAtAnyOf(elements)) {
      myIterator.retreat();
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
  
  public SemanticEditorPosition afterOptional(@NotNull SyntaxElement syntaxElement) {
    if (!myIterator.atEnd()) {
      if (syntaxElement.equals(map(myIterator.getTokenType()))) myIterator.advance();
    }
    return this;
  }
  
  public SemanticEditorPosition after() {
    if (!myIterator.atEnd()) {
      myIterator.advance();
    }
    return this;
  }
  
  public SemanticEditorPosition beforeParentheses(@NotNull SyntaxElement leftParenthesis, @NotNull SyntaxElement rightParenthesis) {
    int parenLevel = 0;
    while (!myIterator.atEnd()) {
      SyntaxElement currElement = map(myIterator.getTokenType());
      myIterator.retreat();
      if (rightParenthesis.equals(currElement)) {
        parenLevel++;
      }
      else if (leftParenthesis.equals(currElement)) {
        parenLevel--;
        if (parenLevel < 1) {
          break;
        }
      }
    }
    return this;
  }

  public SemanticEditorPosition findLeftParenthesisBackwardsSkippingNested(@NotNull SyntaxElement leftParenthesis,
                                                                            @NotNull SyntaxElement rightParenthesis) {
    while (!myIterator.atEnd()) {
      if (rightParenthesis.equals(map(myIterator.getTokenType()))) {
        beforeParentheses(leftParenthesis, rightParenthesis);
      }
      else if (leftParenthesis.equals(map(myIterator.getTokenType()))) {
        break; 
      }
      myIterator.retreat();
    }
    return this;
  }
  
  public boolean isAfterOnSameLine(@NotNull SyntaxElement... syntaxElements) {
    myIterator.retreat();
    while (!myIterator.atEnd() && !isAtMultiline()) {
      SyntaxElement currElement = map(myIterator.getTokenType());
      for (SyntaxElement element : syntaxElements) {
        if (element.equals(currElement)) return true;
      }
      myIterator.retreat();
    }
    return false;
  }
  
  public boolean isAt(@NotNull SyntaxElement syntaxElement) {
    return !myIterator.atEnd() && syntaxElement.equals(map(myIterator.getTokenType()));
  }

  public boolean isAt(@NotNull IElementType elementType) {
    return !myIterator.atEnd() && myIterator.getTokenType() == elementType;
  }
  
  public boolean isAtEnd() {
    return myIterator.atEnd();
  }
  
  public int getStartOffset() {
    return myIterator.getStart();
  }
  
  @SuppressWarnings("unused")
  public boolean isAtAnyOf(@NotNull SyntaxElement... syntaxElements) {
    if (!myIterator.atEnd()) {
      SyntaxElement currElement = map(myIterator.getTokenType());
      for (SyntaxElement element : syntaxElements) {
        if (element.equals(currElement)) return true;
      }
    }
    return false;
  }

  public CharSequence getChars() {
    return myChars;
  }
  
  
  public int findStartOf(@NotNull SyntaxElement element) {
    while (!myIterator.atEnd()) {
      if (element.equals(map(myIterator.getTokenType()))) return myIterator.getStart();
      myIterator.retreat();
    }
    return -1;
  }

  public EditorEx getEditor() {
    return myEditor;
  }
  
  @Nullable
  public Language getLanguage() {
    return !myIterator.atEnd() ? myIterator.getTokenType().getLanguage() : null;
  }
  
  public boolean isAtLanguage(@Nullable Language language) {
    if (language != null && !myIterator.atEnd()) {
      return language== Language.ANY || myIterator.getTokenType().getLanguage().is(language); 
    }
    return false;
  }

  @Nullable
  public SyntaxElement getCurrElement() {
    return !myIterator.atEnd() ? map(myIterator.getTokenType()) : null;
  }
  
  public boolean matchesRule(@NotNull Rule rule) {
    return rule.check(this);
  }

  public interface Rule {
    boolean check(SemanticEditorPosition position);
  }
  
  public abstract SyntaxElement map(@NotNull IElementType elementType);

  @Override
  public String toString() {
    return myIterator.getTokenType().toString();
  }
}

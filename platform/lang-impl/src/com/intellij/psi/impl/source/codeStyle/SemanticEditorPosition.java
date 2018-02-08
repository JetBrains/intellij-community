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
import com.intellij.openapi.editor.ex.util.HighlighterIteratorWrapper;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Rustam Vishnyakov
 */
public class SemanticEditorPosition {
  public interface SyntaxElement {}
  
  private final EditorEx myEditor;
  private final HighlighterIterator myIterator;
  private final CharSequence myChars;
  private final Function<IElementType, SyntaxElement> myTypeMapper;

  public SemanticEditorPosition(@NotNull EditorEx editor, int offset, @NotNull Function<IElementType,SyntaxElement> typeMapper) {
    this(editor, editor.getHighlighter().createIterator(offset), typeMapper);
  }

  private SemanticEditorPosition(@NotNull EditorEx editor,
                                 @NotNull HighlighterIterator iterator,
                                 @NotNull Function<IElementType, SyntaxElement> typeMapper) {
    myEditor = editor;
    myChars = editor.getDocument().getCharsSequence();
    myIterator = iterator;
    myTypeMapper = typeMapper;
  }

  public void moveBeforeOptional(@NotNull SyntaxElement syntaxElement) {
    if (!isAtEnd()) {
      if (syntaxElement.equals(map(myIterator.getTokenType()))) moveBefore();
    }
  }

  public SemanticEditorPosition beforeOptional(@NotNull SyntaxElement syntaxElement) {
    return copyAnd(position -> position.moveBeforeOptional(syntaxElement));
  }
  
  public void moveBeforeOptionalMix(@NotNull SyntaxElement... elements) {
    while (isAtAnyOf(elements)) {
      moveBefore();
    }
  }

  public SemanticEditorPosition beforeOptionalMix(@NotNull SyntaxElement... elements) {
    return copyAnd(position -> position.moveBeforeOptionalMix(elements));
  }
  
  public void moveAfterOptionalMix(@NotNull SyntaxElement... elements)  {
    while (isAtAnyOf(elements)) {
      moveAfter();
    }
  }


  public SemanticEditorPosition afterOptionalMix(@NotNull SyntaxElement... elements) {
    return copyAnd(position -> position.moveAfterOptionalMix(elements));
  }

  public boolean isAtMultiline() {
    if (!isAtEnd()) {
      return CharArrayUtil.containLineBreaks(myChars, myIterator.getStart(), myIterator.getEnd());
    }
    return false;
  }

  public boolean isAtMultiline(SyntaxElement... elements) {
    return isAtAnyOf(elements) && CharArrayUtil.containLineBreaks(myChars, myIterator.getStart(), myIterator.getEnd());
  }
  
  public void moveBefore() {
    if (!isAtEnd()) {
      myIterator.retreat();
    }
  }

  public SemanticEditorPosition before() {
    return copyAnd(position -> position.moveBefore());
  }
  
  public void moveAfterOptional(@NotNull SyntaxElement syntaxElement) {
    if (!isAtEnd()) {
      if (syntaxElement.equals(map(myIterator.getTokenType()))) moveAfter();
    }
  }

  public SemanticEditorPosition afterOptional(@NotNull SyntaxElement syntaxElement) {
    return copyAnd(position -> position.moveAfterOptional(syntaxElement));
  }

  public void moveAfter() {
    if (!isAtEnd()) {
      myIterator.advance();
    }
  }

  public SemanticEditorPosition after() {
    return copyAnd(position -> position.moveAfter());
  }
  
  public void moveBeforeParentheses(@NotNull SyntaxElement leftParenthesis, @NotNull SyntaxElement rightParenthesis) {
    int parenLevel = 0;
    while (!isAtEnd()) {
      SyntaxElement currElement = map(myIterator.getTokenType());
      moveBefore();
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
  }

  public SemanticEditorPosition beforeParentheses(@NotNull SyntaxElement leftParenthesis, @NotNull SyntaxElement rightParenthesis) {
    return copyAnd(position -> position.moveBeforeParentheses(leftParenthesis, rightParenthesis));
  }

  public void moveToLeftParenthesisBackwardsSkippingNested(@NotNull SyntaxElement leftParenthesis,
                                                           @NotNull SyntaxElement rightParenthesis) {
    moveToLeftParenthesisBackwardsSkippingNested(leftParenthesis, rightParenthesis, Conditions.alwaysFalse());
  }

  public SemanticEditorPosition findLeftParenthesisBackwardsSkippingNested(@NotNull SyntaxElement leftParenthesis,
                                                                           @NotNull SyntaxElement rightParenthesis) {
    return copyAnd(position -> position.moveToLeftParenthesisBackwardsSkippingNested(leftParenthesis, rightParenthesis));
  }
  
  public void moveToLeftParenthesisBackwardsSkippingNested(@NotNull SyntaxElement leftParenthesis,
                                                           @NotNull SyntaxElement rightParenthesis,
                                                           @NotNull Condition<SyntaxElement> terminationCondition) {
    while (!isAtEnd()) {
      if (terminationCondition.value(map(myIterator.getTokenType()))) {
        break;
      }
      if (rightParenthesis.equals(map(myIterator.getTokenType()))) {
        moveBeforeParentheses(leftParenthesis, rightParenthesis);
      }
      else if (leftParenthesis.equals(map(myIterator.getTokenType()))) {
        break; 
      }
      moveBefore();
    }
  }

  public SemanticEditorPosition findLeftParenthesisBackwardsSkippingNested(@NotNull SyntaxElement leftParenthesis,
                                                                           @NotNull SyntaxElement rightParenthesis,
                                                                           @NotNull Condition<SyntaxElement> terminationCondition) {
    return copyAnd(
      position -> position.moveToLeftParenthesisBackwardsSkippingNested(leftParenthesis, rightParenthesis, terminationCondition));
  }

  public boolean isAfterOnSameLine(@NotNull SyntaxElement... syntaxElements) {
    return elementAfterOnSameLine(syntaxElements) != null;
  }

  @Nullable
  public SyntaxElement elementAfterOnSameLine(@NotNull SyntaxElement... syntaxElements) {
    moveBefore();
    while (!isAtEnd() && !isAtMultiline()) {
      SyntaxElement currElement = map(myIterator.getTokenType());
      for (SyntaxElement element : syntaxElements) {
        if (element.equals(currElement)) return element;
      }
      moveBefore();
    }
    return null;
  }
  
  public boolean isAt(@NotNull SyntaxElement syntaxElement) {
    return !isAtEnd() && syntaxElement.equals(map(myIterator.getTokenType()));
  }

  public boolean isAt(@NotNull IElementType elementType) {
    return !isAtEnd() && myIterator.getTokenType() == elementType;
  }
  
  public boolean isAtEnd() {
    return myIterator.atEnd();
  }
  
  public int getStartOffset() {
    return myIterator.getStart();
  }

  @SuppressWarnings("unused")
  public boolean isAtAnyOf(@NotNull SyntaxElement... syntaxElements) {
    if (!isAtEnd()) {
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
    while (!isAtEnd()) {
      if (element.equals(map(myIterator.getTokenType()))) return myIterator.getStart();
      moveBefore();
    }
    return -1;
  }

  public boolean hasEmptyLineAfter(int offset) {
    for (int i = offset + 1; i < myIterator.getEnd(); i++) {
      if (myChars.charAt(i) == '\n') return true;
    }
    return false;
  }

  public EditorEx getEditor() {
    return myEditor;
  }
  
  @Nullable
  public Language getLanguage() {
    return !isAtEnd() ? myIterator.getTokenType().getLanguage() : null;
  }
  
  public boolean isAtLanguage(@Nullable Language language) {
    if (language != null && !isAtEnd()) {
      return language== Language.ANY || myIterator.getTokenType().getLanguage().is(language); 
    }
    return false;
  }

  @Nullable
  public SyntaxElement getCurrElement() {
    return !isAtEnd() ? map(myIterator.getTokenType()) : null;
  }
  
  public boolean matchesRule(@NotNull Rule rule) {
    return rule.check(this);
  }

  public interface Rule {
    boolean check(SemanticEditorPosition position);
  }
  
  public SyntaxElement map(@NotNull IElementType elementType) {
    return myTypeMapper.apply(elementType);
  }

  @Override
  public String toString() {
    return myIterator.getTokenType().toString();
  }

  public SemanticEditorPosition copy() {
    if (!isAtEnd()) {
      return createEditorPosition(myEditor, myIterator.getStart(), myTypeMapper);
    }
    // A wrapper around current iterator to make it immutable.
    HighlighterIteratorWrapper wrapper = new HighlighterIteratorWrapper(myIterator) {
      @Override
      public void advance() {
        // do nothing
      }
      @Override
      public void retreat() {
        // do nothing
      }
    };
    return new SemanticEditorPosition(myEditor, wrapper, myTypeMapper);
  }

  @NotNull
  protected SemanticEditorPosition createEditorPosition(@NotNull EditorEx editor,
                                                        int start,
                                                        @NotNull Function<IElementType, SyntaxElement> mapper) {
    return new SemanticEditorPosition(editor, start, mapper);
  }

  public SemanticEditorPosition copyAnd(@NotNull Consumer<SemanticEditorPosition> modifier) {
    SemanticEditorPosition position = copy();
    modifier.accept(position);
    return position;
  }
}

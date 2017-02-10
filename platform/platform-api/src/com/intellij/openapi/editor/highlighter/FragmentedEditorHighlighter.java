/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/8/11
 * Time: 12:52 PM
 */
public class FragmentedEditorHighlighter implements EditorHighlighter {
  private final List<Element> myPieces;
  private final Document myDocument;
  private final int myAdditionalOffset;
  private final boolean myMergeByTextAttributes;

  public FragmentedEditorHighlighter(HighlighterIterator sourceIterator, List<TextRange> ranges) {
    this(sourceIterator, ranges, 0, false);
  }

  public FragmentedEditorHighlighter(HighlighterIterator sourceIterator,
                                     List<TextRange> ranges,
                                     final int additionalOffset,
                                     boolean mergeByTextAttributes) {
    myMergeByTextAttributes = mergeByTextAttributes;
    myDocument = sourceIterator.getDocument();
    myPieces = new ArrayList<>();
    myAdditionalOffset = additionalOffset;
    translate(sourceIterator, ranges);
  }

  private void translate(HighlighterIterator iterator, List<TextRange> ranges) {
    int offset = 0;
    int index = 0;

    while (!iterator.atEnd() && index < ranges.size()) {
      TextRange range = ranges.get(index);

      if (range.getStartOffset() >= iterator.getEnd()) {
        iterator.advance();
        continue;
      }

      if (range.getEndOffset() >= iterator.getStart()) {
        int relativeStart = Math.max(iterator.getStart() - range.getStartOffset(), 0);
        int relativeEnd = Math.min(iterator.getEnd() - range.getStartOffset(), range.getLength() + 1);

        addElement(new Element(offset + relativeStart,
                               offset + relativeEnd,
                               iterator.getTokenType(),
                               iterator.getTextAttributes()));
      }

      if (range.getEndOffset() < iterator.getEnd()) {
        offset += range.getLength() + 1 + myAdditionalOffset;  // myAdditionalOffset because of extra line - for shoene separators
        int lastEnd = myPieces.isEmpty() ? -1 : myPieces.get(myPieces.size() - 1).getEnd();
        addElement(new Element(Math.max(offset - 1 - myAdditionalOffset, lastEnd), offset, null, TextAttributes.ERASE_MARKER));
        index++;
        continue;
      }

      iterator.advance();
    }
  }

  private void addElement(@NotNull Element element) {
    boolean merged = false;
    if (myMergeByTextAttributes && !myPieces.isEmpty()) {
      Element oldElement = myPieces.get(myPieces.size() - 1);
      if (oldElement.getEnd() >= element.getStart() &&
          Comparing.equal(oldElement.getAttributes(), element.getAttributes()) &&
          Comparing.equal(oldElement.getElementType(), element.getElementType())) {
        merged = true;
        myPieces.remove(myPieces.size() - 1);
        myPieces.add(new Element(oldElement.getStart(),
                                 element.getEnd(),
                                 element.getElementType(),
                                 element.getAttributes()));
      }
    }
    if (!merged) {
      myPieces.add(element);
    }
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(int startOffset) {
    int index = Collections.binarySearch(myPieces, new Element(startOffset, 0, null, null), (o1, o2) -> o1.getStart() - o2.getStart());
    // index: (-insertion point - 1), where insertionPoint is the index of the first element greater than the key
    // and we need index of the first element that is less or equal (floorElement)
    if (index < 0) index = Math.max(-index - 2, 0);
    return new ProxyIterator(myDocument, index);
  }

  @Override
  public void setText(@NotNull CharSequence text) {
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
  }

  private class ProxyIterator implements HighlighterIterator {
    private final Document myDocument;
    private int myIdx;

    private ProxyIterator(Document document, int idx) {
      myDocument = document;
      myIdx = idx;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myPieces.get(myIdx).getAttributes();
    }

    @Override
    public int getStart() {
      return myPieces.get(myIdx).getStart();
    }

    @Override
    public int getEnd() {
      return myPieces.get(myIdx).getEnd();
    }

    @Override
    public IElementType getTokenType() {
      return myPieces.get(myIdx).myElementType;
    }

    @Override
    public void advance() {
      if (myIdx < myPieces.size()) {
        myIdx++;
      }
    }

    @Override
    public void retreat() {
      if (myIdx > -1) {
        myIdx--;
      }
    }

    @Override
    public boolean atEnd() {
      return myIdx < 0 || myIdx >= myPieces.size();
    }

    @Override
    public Document getDocument() {
      return myDocument;
    }
  }

  private static class Element {
    private final int myStart;
    private final int myEnd;
    private final IElementType myElementType;
    private final TextAttributes myAttributes;

    private Element(int start, int end, IElementType elementType, TextAttributes attributes) {
      myStart = start;
      myEnd = end;
      myElementType = elementType;
      myAttributes = attributes;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }

    public IElementType getElementType() {
      return myElementType;
    }

    public TextAttributes getAttributes() {
      return myAttributes;
    }
  }
}

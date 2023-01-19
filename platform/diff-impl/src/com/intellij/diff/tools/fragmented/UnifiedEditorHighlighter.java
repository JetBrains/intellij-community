// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.fragmented;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class UnifiedEditorHighlighter implements EditorHighlighter {
  private static final Logger LOG = Logger.getInstance(UnifiedEditorHighlighter.class);

  @NotNull private final Document myDocument;
  @NotNull private final List<Element> myPieces;

  UnifiedEditorHighlighter(@NotNull Document document,
                                  @NotNull EditorHighlighter highlighter1,
                                  @NotNull EditorHighlighter highlighter2,
                                  @NotNull List<HighlightRange> ranges,
                                  int textLength) {
    myDocument = document;
    myPieces = new ArrayList<>();
    init(highlighter1.createIterator(0), highlighter2.createIterator(0), ranges, textLength);
  }

  private void init(@NotNull HighlighterIterator it1,
                    @NotNull HighlighterIterator it2,
                    @NotNull List<? extends HighlightRange> ranges,
                    int textLength) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    int i = 0;
    int offset = 0;

    for (HighlightRange range : ranges) {
      TextRange base = range.getBase();
      TextRange changed = range.getChanged();

      if (base.isEmpty()) continue;

      if (base.getStartOffset() > offset) {
        addElement(new Element(offset, base.getStartOffset(), null, TextAttributes.ERASE_MARKER));
        offset = base.getStartOffset();
      }

      HighlighterIterator it = range.getSide().select(it1, it2);
      while (!it.atEnd() && changed.getStartOffset() >= it.getEnd()) {
        if (i++ % 1024 == 0) ProgressManager.checkCanceled();
        it.advance();
      }

      if (it.atEnd()) {
        LOG.error("Unexpected end of highlighter");
        break;
      }

      if (changed.getEndOffset() <= it.getStart()) {
        continue;
      }

      while (true) {
        int relativeStart = Math.max(it.getStart() - changed.getStartOffset(), 0);
        int relativeEnd = Math.min(it.getEnd() - changed.getStartOffset(), changed.getLength() + 1);

        addElement(new Element(offset + relativeStart,
                               offset + relativeEnd,
                               it.getTokenType(),
                               it.getTextAttributes()));

        if (changed.getEndOffset() <= it.getEnd()) {
          offset += changed.getLength();
          break;
        }

        if (i++ % 1024 == 0) ProgressManager.checkCanceled();
        it.advance();
        if (it.atEnd()) {
          LOG.error("Unexpected end of highlighter");
          break;
        }
      }
    }

    if (offset < textLength) {
      addElement(new Element(offset, textLength, null, TextAttributes.ERASE_MARKER));
    }
  }

  private void addElement(@NotNull Element element) {
    boolean merged = false;
    if (!myPieces.isEmpty()) {
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
    int index = Collections.binarySearch(myPieces, new Element(startOffset, 0, null, TextAttributes.ERASE_MARKER), Comparator.comparingInt(Element::getStart));
    // index: (-insertion point - 1), where insertionPoint is the index of the first element greater than the key
    // and we need index of the first element that is less or equal (floorElement)
    if (index < 0) index = Math.max(-index - 2, 0);
    return new ProxyIterator(myDocument, index, myPieces);
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
  }

  private static final class ProxyIterator implements HighlighterIterator {
    @NotNull
    private final Document myDocument;
    private int myIdx;
    private final List<Element> myPieces;

    private ProxyIterator(@NotNull Document document, int idx, @NotNull List<Element> pieces) {
      myDocument = document;
      myIdx = idx;
      myPieces = pieces;
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

    @NotNull
    @Override
    public Document getDocument() {
      return myDocument;
    }
  }

  private static final class Element {
    private final int myStart;
    private final int myEnd;
    private final IElementType myElementType;
    private final TextAttributes myAttributes;

    private Element(int start, int end, IElementType elementType, @NotNull TextAttributes attributes) {
      myStart = start;
      myEnd = end;
      myElementType = elementType;
      myAttributes = attributes;
    }

    int getStart() {
      return myStart;
    }

    int getEnd() {
      return myEnd;
    }

    IElementType getElementType() {
      return myElementType;
    }

    @NotNull TextAttributes getAttributes() {
      return myAttributes;
    }
  }
}

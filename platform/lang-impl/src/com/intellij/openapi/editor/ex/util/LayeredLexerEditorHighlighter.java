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

package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.MergingCharSequence;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class LayeredLexerEditorHighlighter extends LexerEditorHighlighter {
  private final Map<IElementType, LayerDescriptor> myTokensToLayer = new HashMap<IElementType, LayerDescriptor>();
  private final Map<LayerDescriptor, Mapper> myLayerBuffers = new HashMap<LayerDescriptor, Mapper>();
  private final MappingSegments mySegments = new MappingSegments();
  private CharSequence myText;

  public LayeredLexerEditorHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme scheme) {
    super(highlighter, scheme);
    setSegmentStorage(mySegments);
  }

  public synchronized void registerLayer(IElementType tokenType, LayerDescriptor layerHighlighter) {
    myTokensToLayer.put(tokenType, layerHighlighter);
    mySegments.removeAll();
  }

  public synchronized void unregisterLayer(IElementType tokenType) {
    final LayerDescriptor layer = myTokensToLayer.remove(tokenType);
    if (layer != null) {
      myLayerBuffers.remove(layer);
      mySegments.removeAll();
    }
  }

  private class LightMapper {
    final Mapper mapper;
    final StringBuilder text = new StringBuilder();
    final IntArrayList lengths = new IntArrayList();
    final List<IElementType> tokenTypes = new ArrayList<IElementType>();
    final TIntIntHashMap index2Global = new TIntIntHashMap();
    private final String mySeparator;
    final int insertOffset;

    LightMapper(final Mapper mapper, int insertOffset) {
      this.mapper = mapper;
      mySeparator = mapper.mySeparator;
      this.insertOffset = insertOffset;
    }

    void addToken(CharSequence tokenText, IElementType tokenType, int globalIndex) {
      index2Global.put(tokenTypes.size(), globalIndex);
      text.append(mySeparator).append(tokenText);
      lengths.add(tokenText.length());
      tokenTypes.add(tokenType);
    }

    void finish() {
      assert insertOffset >= 0;
      final DocumentImpl document = mapper.doc;
      document.insertString(insertOffset, text);
      int start = insertOffset;
      for (int i = 0; i < tokenTypes.size(); i++) {
        IElementType type = tokenTypes.get(i);
        final int len = lengths.get(i);
        start += mySeparator.length();
        final int globalIndex = index2Global.get(i);
        assert mySegments.myRanges[globalIndex] == null : myText;
        mySegments.myRanges[globalIndex] = new MappedRange(mapper, document.createRangeMarker(start, start + len), type);
        start += len;
      }
    }
  }

  public void setText(final CharSequence text) {
    // do NOT synchronize before updateLayers due to deadlock with PsiLock
    updateLayers();

    myText = text;
    super.setText(text);
  }

  @Override
  protected TokenProcessor createTokenProcessor(final int startIndex) {
    return new TokenProcessor() {
      final Map<Mapper, LightMapper> docTexts = new FactoryMap<Mapper, LightMapper>() {
        @Override
        protected LightMapper create(final Mapper key) {
          final MappedRange predecessor = key.findPredecessor(startIndex);
          return new LightMapper(key, predecessor != null ? predecessor.range.getEndOffset() : 0);
        }
      };

      public void addToken(final int i, final int startOffset, final int endOffset, final int data, final IElementType tokenType) {
        mySegments.setElementLight(i, startOffset, endOffset, data);
        final Mapper mapper = getMappingDocument(tokenType);
        if (mapper != null) {
          docTexts.get(mapper).addToken(myText.subSequence(startOffset, endOffset), tokenType, i);
        }
      }

      @Override
      public void finish() {
        for (final LightMapper mapper : docTexts.values()) {
          mapper.finish();
        }
      }
    };
  }

  protected boolean updateLayers() { return false; }

  public void documentChanged(DocumentEvent e) {
    // do NOT synchronize before updateLayers due to deadlock with PsiLock
    final boolean b = updateLayers();

    synchronized (this) {
      myText = e.getDocument().getCharsSequence();
      if (b) {
        setText(myText);
      }
      else {
        super.documentChanged(e);
      }
    }
  }

  public HighlighterIterator createIterator(int startOffset) {
    // do NOT synchronize before updateLayers due to deadlock with PsiLock
    final boolean b = updateLayers();

    synchronized (this) {
      if (b) {
        setText(myText);
      }
      return new LayeredHighlighterIterator(startOffset);
    }
  }

  private class MappingSegments extends SegmentArrayWithData {
    MappedRange[] myRanges = new MappedRange[INITIAL_SIZE];

    public void removeAll() {
      if (mySegmentCount != 0) {
        Arrays.fill(myRanges, null);
      }

      myLayerBuffers.clear();

      super.removeAll();
    }

    public void setElementAt(int i, int startOffset, int endOffset, int data) {
      setElementLight(i, startOffset, endOffset, (short)data);
      final MappedRange range = myRanges[i];
      if (range != null) {
        range.mapper.removeMapping(range);
        myRanges[i] = null;
      }

      updateMappingForToken(i);
    }

    private void setElementLight(final int i, final int startOffset, final int endOffset, final int data) {
      super.setElementAt(i, startOffset, endOffset, data);
      myRanges = reallocateArray(myRanges, i+1);
    }

    public void remove(int startIndex, int endIndex) {
      Map<Mapper, Integer> mins = new FactoryMap<Mapper, Integer>() {
        @Override
        protected Integer create(final Mapper key) {
          return Integer.MAX_VALUE;
        }
      };
      Map<Mapper, Integer> maxs = new FactoryMap<Mapper, Integer>() {
        @Override
        protected Integer create(final Mapper key) {
          return 0;
        }
      };

      for (int i = startIndex; i < endIndex; i++) {
        final MappedRange range = myRanges[i];
        if (range != null && range.range.isValid()) {
          mins.put(range.mapper, Math.min(mins.get(range.mapper).intValue(), range.range.getStartOffset()));
          maxs.put(range.mapper, Math.max(maxs.get(range.mapper).intValue(), range.range.getEndOffset()));
        }

        myRanges[i] = null;
      }
      for (final Mapper mapper : maxs.keySet()) {
        mapper.doc.deleteString(mins.get(mapper).intValue(), maxs.get(mapper).intValue());
      }

      myRanges = remove(myRanges, startIndex, endIndex);
      super.remove(startIndex, endIndex);
    }

    public void replace(int startOffset, SegmentArrayWithData data, int len) {
      super.replace(startOffset, data, len);
      for (int i = startOffset; i < startOffset + len; i++) {
        updateMappingForToken(i);
      }
    }

    public void insert(SegmentArrayWithData segmentArray, final int startIndex) {
      synchronized (LayeredLexerEditorHighlighter.this) {
        super.insert(segmentArray, startIndex);

        final int newCount = segmentArray.getSegmentCount();
        final MappedRange[] newRanges = new MappedRange[newCount];

        myRanges = insert(myRanges, newRanges, startIndex, newCount);

        int endIndex = startIndex + segmentArray.getSegmentCount();

        TokenProcessor processor = createTokenProcessor(startIndex);
        for (int i = startIndex; i < endIndex; i++) {
          final short data = getSegmentData(i);
          final IElementType token = unpackToken(data);
          processor.addToken(i, mySegments.getSegmentStart(i), mySegments.getSegmentEnd(i), data, token);
        }

        processor.finish();
      }
    }

    private void updateMappingForToken(final int i) {
      final short data = getSegmentData(i);
      final IElementType token = unpackToken(data);
      final Mapper mapper = getMappingDocument(token);
      final MappedRange oldMapping = myRanges[i];
      if (mapper != null) {
        if (oldMapping != null) {
          if (oldMapping.mapper == mapper && oldMapping.outerToken == token) {
            mapper.updateMapping(i, oldMapping);
          }
          else {
            oldMapping.mapper.removeMapping(oldMapping);
            myRanges[i] = mapper.insertMapping(i, token);
          }
        }
        else {
          myRanges[i] = mapper.insertMapping(i, token);
        }
      }
      else {
        if (oldMapping != null) {
          oldMapping.mapper.removeMapping(oldMapping);
          myRanges[i] = null;
        }
      }
    }
  }

  private class Mapper implements HighlighterClient {
    private final DocumentImpl doc;
    private final EditorHighlighter highlighter;
    private final String mySeparator;
    private final Map<IElementType, TextAttributes> myAttributesMap = new HashMap<IElementType, TextAttributes>();
    private final SyntaxHighlighter mySyntaxHighlighter;
    private final TextAttributesKey myBackground;


    private Mapper(LayerDescriptor descriptor) {
      doc = new DocumentImpl(true);

      mySyntaxHighlighter = descriptor.getLayerHighlighter();
      myBackground = descriptor.getBackgroundKey();
      highlighter = new LexerEditorHighlighter(mySyntaxHighlighter, getScheme());
      mySeparator = descriptor.getTokenSeparator();
      highlighter.setEditor(this);
      doc.addDocumentListener(highlighter);
    }

    public TextAttributes getAttributes(IElementType tokenType) {
      TextAttributes attrs = myAttributesMap.get(tokenType);
      if (attrs == null) {
        attrs = convertAttributes(SyntaxHighlighterBase.pack(mySyntaxHighlighter.getTokenHighlights(tokenType), myBackground));
        myAttributesMap.put(tokenType, attrs);
      }
      return attrs;
    }

    public HighlighterIterator createIterator(MappedRange mapper, int shift) {
      final int rangeStart = mapper.range.getStartOffset();
      final int rangeEnd = mapper.range.getEndOffset();
      return new LimitedRangeHighlighterIterator(highlighter.createIterator(rangeStart + shift), rangeStart, rangeEnd);
    }

    public Project getProject() {
      return getClient().getProject();
    }

    public void repaint(int start, int end) {
      // TODO: map ranges to outer document
    }

    public Document getDocument() {
      return LayeredLexerEditorHighlighter.this.getDocument();
    }

    public void updateMapping(final int tokenIndex, final MappedRange oldMapping) {
      CharSequence tokenText = getTokenText(tokenIndex);

      final int start = oldMapping.range.getStartOffset();
      final int end = oldMapping.range.getEndOffset();
      if (Comparing.equal(doc.getCharsSequence().subSequence(start, end), tokenText)) return;

      doc.replaceString(start, end, tokenText);

      final int newEnd = start + tokenText.length();
      if (oldMapping.range.getStartOffset() != start ||
          oldMapping.range.getEndOffset() != newEnd
         ) {
        oldMapping.range = doc.createRangeMarker(start, newEnd);
      }
    }

    public MappedRange insertMapping(int tokenIndex, IElementType outerToken) {
      CharSequence tokenText = getTokenText(tokenIndex);

      final int length = tokenText.length();

      MappedRange predecessor = findPredecessor(tokenIndex);

      int insertOffset = predecessor != null ? predecessor.range.getEndOffset() : 0;
      doc.insertString(insertOffset, new MergingCharSequence(mySeparator, tokenText));
      insertOffset += mySeparator.length();

      return new MappedRange(this, doc.createRangeMarker(insertOffset, insertOffset + length), outerToken);
    }

    private CharSequence getTokenText(final int tokenIndex) {
      return myText.subSequence(mySegments.getSegmentStart(tokenIndex), mySegments.getSegmentEnd(tokenIndex));
    }

    @Nullable
    private MappedRange findPredecessor(int token) {
      token--;
      while (token >= 0) {
        final MappedRange mappedRange = mySegments.myRanges[token];
        if (mappedRange != null && mappedRange.mapper == this) return mappedRange;
        token--;
      }

      return null;
    }

    public void removeMapping(MappedRange mapping) {
      RangeMarker rangeMarker = mapping.range;
      if (rangeMarker.isValid()) {
        final int start = rangeMarker.getStartOffset();
        final int end = rangeMarker.getEndOffset();
        doc.deleteString(start - mySeparator.length(), end);
      }
    }
  }

  private static class MappedRange {
    RangeMarker range;
    final Mapper mapper;
    final IElementType outerToken;

    MappedRange(final Mapper mapper, final RangeMarker range, final IElementType outerToken) {
      this.mapper = mapper;
      this.range = range;
      this.outerToken = outerToken;
    }
  }

  @Nullable
  private Mapper getMappingDocument(IElementType token) {
    final LayerDescriptor descriptor = myTokensToLayer.get(token);
    if (descriptor == null) return null;

    Mapper mapper = myLayerBuffers.get(descriptor);
    if (mapper == null) {
      mapper = new Mapper(descriptor);
      myLayerBuffers.put(descriptor, mapper);
    }

    return mapper;
  }

  private class LayeredHighlighterIterator implements HighlighterIterator {
    private final HighlighterIterator myBaseIterator;
    private HighlighterIterator myLayerIterator;
    private int myLayerStartOffset = 0;
    private Mapper myCurrentMapper;

    private LayeredHighlighterIterator(int offset) {
      myBaseIterator = LayeredLexerEditorHighlighter.super.createIterator(offset);
      if (!myBaseIterator.atEnd()) {
        int shift = offset - myBaseIterator.getStart();
        initLayer(shift);
      }
    }

    private void initLayer(final int shiftInToken) {
      if (myBaseIterator.atEnd()) {
        myLayerIterator = null;
        myCurrentMapper = null;
        return;
      }

      MappedRange mapping = mySegments.myRanges[((HighlighterIteratorImpl)myBaseIterator).currentIndex()];
      if (mapping != null) {
        myCurrentMapper = mapping.mapper;
        myLayerIterator = myCurrentMapper.createIterator(mapping, shiftInToken);
        myLayerStartOffset = myBaseIterator.getStart() - mapping.range.getStartOffset();
      }
      else {
        myCurrentMapper = null;
        myLayerIterator = null;
      }
    }

    public TextAttributes getTextAttributes() {
      if (myCurrentMapper != null) {
        return myCurrentMapper.getAttributes(getTokenType());
      }

      return myBaseIterator.getTextAttributes();
    }

    public int getStart() {
      if (myLayerIterator != null) {
        return myLayerIterator.getStart() + myLayerStartOffset;
      }
      return myBaseIterator.getStart();
    }

    public int getEnd() {
      if (myLayerIterator != null) {
        return myLayerIterator.getEnd() + myLayerStartOffset;
      }
      return myBaseIterator.getEnd();
    }

    public IElementType getTokenType() {
      return myLayerIterator != null ? myLayerIterator.getTokenType() : myBaseIterator.getTokenType();
    }

    public void advance() {
      if (myLayerIterator != null) {
        myLayerIterator.advance();
        if (!myLayerIterator.atEnd()) return;
      }
      myBaseIterator.advance();
      initLayer(0);
    }

    public void retreat() {
      if (myLayerIterator != null) {
        myLayerIterator.retreat();
        if (!myLayerIterator.atEnd()) return;
      }

      myBaseIterator.retreat();
      initLayer(myBaseIterator.atEnd() ? 0 : myBaseIterator.getEnd() - myBaseIterator.getStart() - 1);
    }

    public boolean atEnd() {
      return myBaseIterator.atEnd();
    }

    public Document getDocument() {
      return myBaseIterator.getDocument();
    }
  }
}

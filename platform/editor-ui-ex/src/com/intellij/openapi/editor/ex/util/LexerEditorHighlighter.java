/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import com.intellij.util.text.MergingCharSequence;
import com.intellij.util.text.SingleCharSequence;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LexerEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.LexerEditorHighlighter");
  private static final int LEXER_INCREMENTALITY_THRESHOLD = 200;
  private static final Set<Class> ourNonIncrementalLexers = new HashSet<>();
  private HighlighterClient myEditor;
  private final Lexer myLexer;
  private final Map<IElementType, TextAttributes> myAttributesMap = new HashMap<>();
  private final SegmentArrayWithData mySegments;
  private final SyntaxHighlighter myHighlighter;
  private EditorColorsScheme myScheme;
  private final int myInitialState;
  protected CharSequence myText;

  public LexerEditorHighlighter(@NotNull SyntaxHighlighter highlighter, @NotNull EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myLexer.start(ArrayUtil.EMPTY_CHAR_SEQUENCE);
    myInitialState = myLexer.getState();
    myHighlighter = highlighter;
    mySegments = createSegments();
  }

  protected SegmentArrayWithData createSegments() {
    return new SegmentArrayWithData();
  }

  public boolean isPlain() {
    return myHighlighter instanceof PlainSyntaxHighlighter;
  }

  @Nullable
  protected final Document getDocument() {
    return myEditor != null ? myEditor.getDocument() : null;
  }

  public final synchronized boolean checkContentIsEqualTo(CharSequence sequence) {
    final Document document = getDocument();
    return document != null && isInSyncWithDocument() && Comparing.equal(document.getImmutableCharSequence(), sequence);
  }

  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  protected Lexer getLexer() {
    return myLexer;
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
    myScheme = scheme;
    myAttributesMap.clear();
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(int startOffset) {
    synchronized (this) {
      if (!isInSyncWithDocument()) {
        final Document document = getDocument();
        assert document != null;
        if(document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) {
          ((DocumentEx)document).setInBulkUpdate(false); // bulk mode failed
        }
        doSetText(document.getImmutableCharSequence());
      }

      final int latestValidOffset = mySegments.getLastValidOffset();
      return new HighlighterIteratorImpl(startOffset <= latestValidOffset ? startOffset : latestValidOffset);
    }
  }

  private int packData(IElementType tokenType, int state) {
    final short idx = tokenType.getIndex();
    return state == myInitialState ? idx : -idx;
  }

  public boolean isValid() {
    Project project = myEditor.getProject();
    return project != null && !project.isDisposed();
  }
  
  private boolean isInSyncWithDocument() {
    Document document = getDocument();
    return document == null || document.getTextLength() == 0 || mySegments.getSegmentCount() > 0;
  }

  private static boolean isInitialState(int data) {
    return data >= 0;
  }

  protected static IElementType unpackToken(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  @Override
  public synchronized void documentChanged(DocumentEvent e) {
    try {
      final Document document = e.getDocument();
      CharSequence text = document.getImmutableCharSequence();

      if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) {
        myText = null;
        mySegments.removeAll();
        return;
      }

      if(mySegments.getSegmentCount() == 0) {
        setText(text);
        return;
      }

      myText = text;
      int oldStartOffset = e.getOffset();

      final int segmentIndex = mySegments.findSegmentIndex(oldStartOffset) - 2;
      final int oldStartIndex = Math.max(0, segmentIndex);
      int startIndex = oldStartIndex;

      int data;
      do {
        data = mySegments.getSegmentData(startIndex);
        if (isInitialState(data)|| startIndex == 0) break;
        startIndex--;
      }
      while (true);

      int startOffset = mySegments.getSegmentStart(startIndex);
      int newEndOffset = e.getOffset() + e.getNewLength();

      myLexer.start(text, startOffset, text.length(), myInitialState);

      int lastTokenStart = -1;
      int lastLexerState = -1;
      IElementType lastTokenType = null;

      while (myLexer.getTokenType() != null) {
        if (startIndex >= oldStartIndex) break;

        int tokenStart = myLexer.getTokenStart();
        int lexerState = myLexer.getState();

        if (tokenStart == lastTokenStart && lexerState == lastLexerState && myLexer.getTokenType() == lastTokenType) {
          throw new IllegalStateException("Lexer is not progressing after calling advance()");
        }

        int tokenEnd = myLexer.getTokenEnd();
        data = packData(myLexer.getTokenType(), lexerState);
        if (mySegments.getSegmentStart(startIndex) != tokenStart ||
            mySegments.getSegmentEnd(startIndex) != tokenEnd ||
            mySegments.getSegmentData(startIndex) != data) {
          break;
        }
        startIndex++;
        lastTokenType = myLexer.getTokenType();
        myLexer.advance();
        lastTokenStart = tokenStart;
        lastLexerState = lexerState;
      }

      /*
        Highlighting lexer is expected to periodically return to its "initial state" and
        so to denote valid starting points for incremental highlighting.

        If this requirement is unfulfiled, document has to be always re-analyzed from the beginning
        up to the point of modification,  which can hog CPU and make typing / editing very sluggish,
        especially at large offsets (with at least O(n) time complexity).

        As the faulty lexer implementations otherwise behave normally, it's often hard to spot the problem in the wild.
        Despite additng LexerTestCase.checkCorrectRestart and LexerTestCase.checkZeroState checks and fixing many lexers,
        it's still not so unusual to discover a further broken lexer through pure luck.

        The following runtime check reports cases when document has to be re-analyzed from 0 offset and
        the number of traversed tokens is greater than a predefined threshold.

        Because many highlighting lexers are implemented via the LayeredLexer which forces non-initial state
        (and thus suppresses incrementality) within layers, some false-positivess are probable.
        For example, it's possible to trigger the warning by creating a file with a really large comment
        right at the beginning, and then to modify text at the end of that comment.
        However, this seems to be a rather unusual use case, so that the gain from detecting faulty
        lexers (including third-party ones) justifies the check.

        In a sense, the warning is always righteous, as even with proper layered lexers there really is
        no incrementality within layers, which might lead to performance problem in corresponding cases.
       */
      if (ApplicationManager.getApplication().isInternal() &&
          startOffset == 0 && startIndex > LEXER_INCREMENTALITY_THRESHOLD) {

        Class lexerClass = myLexer.getClass();

        if (!ourNonIncrementalLexers.contains(lexerClass)) {
          LOG.warn(String.format("%s is probably not incremental: no initial state throughout %d tokens",
                                 lexerClass.getName(), startIndex));

          ourNonIncrementalLexers.add(lexerClass);
        }
      }

      startOffset = mySegments.getSegmentStart(startIndex);
      int repaintEnd = -1;
      int insertSegmentCount = 0;
      int oldEndIndex = -1;
      lastTokenType = null;
      SegmentArrayWithData insertSegments = new SegmentArrayWithData();

      while(myLexer.getTokenType() != null) {
        int tokenStart = myLexer.getTokenStart();
        int lexerState = myLexer.getState();

        if (tokenStart == lastTokenStart && lexerState == lastLexerState && myLexer.getTokenType() == lastTokenType) {
          throw new IllegalStateException("Lexer is not progressing after calling advance()");
        }

        lastTokenStart = tokenStart;
        lastLexerState = lexerState;
        lastTokenType = myLexer.getTokenType();

        int tokenEnd = myLexer.getTokenEnd();
        data = packData(myLexer.getTokenType(), lexerState);
        if(tokenStart >= newEndOffset && lexerState == myInitialState) {
          int shiftedTokenStart = tokenStart - e.getNewLength() + e.getOldLength();
          int index = mySegments.findSegmentIndex(shiftedTokenStart);
          if (mySegments.getSegmentStart(index) == shiftedTokenStart && mySegments.getSegmentData(index) == data) {
            repaintEnd = tokenStart;
            oldEndIndex = index;
            break;
          }
        }
        insertSegments.setElementAt(insertSegmentCount, tokenStart, tokenEnd, data);
        insertSegmentCount++;
        myLexer.advance();
      }

      final int shift = e.getNewLength() - e.getOldLength();
      if (repaintEnd > 0) {
        while (insertSegmentCount > 0 && oldEndIndex > startIndex) {
          if (!segmentsEqual(mySegments, oldEndIndex - 1, insertSegments, insertSegmentCount - 1, shift) ||
              hasAdditionalData(oldEndIndex - 1)) {
            break;
          }
          insertSegmentCount--;
          oldEndIndex--;
          repaintEnd = insertSegments.getSegmentStart(insertSegmentCount);
          insertSegments.remove(insertSegmentCount, insertSegmentCount + 1);
        }
      }

      if(repaintEnd == -1) {
        repaintEnd = text.length();
      }

      if (oldEndIndex < 0){
        oldEndIndex = mySegments.getSegmentCount();
      }
      mySegments.shiftSegments(oldEndIndex, shift);
      mySegments.replace(startIndex, oldEndIndex, insertSegments);

      if (insertSegmentCount == 0 ||
          oldEndIndex == startIndex + 1 && insertSegmentCount == 1 && data == mySegments.getSegmentData(startIndex)) {
        return;
      }

      myEditor.repaint(startOffset, repaintEnd);
    }
    catch (ProcessCanceledException ex) {
      myText = null;
      mySegments.removeAll();
      throw ex;
    }
    catch (RuntimeException ex) {
      throw new InvalidStateException(this, "Error updating  after " + e, ex);
    }
  }

  protected boolean hasAdditionalData(int segmentIndex) {
    return false;
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.LEXER_EDITOR;
  }

  private static boolean segmentsEqual(SegmentArrayWithData a1, int idx1, SegmentArrayWithData a2, int idx2, final int offsetShift) {
    return a1.getSegmentStart(idx1) + offsetShift == a2.getSegmentStart(idx2) &&
           a1.getSegmentEnd(idx1) + offsetShift == a2.getSegmentEnd(idx2) &&
           a1.getSegmentData(idx1) == a2.getSegmentData(idx2);
  }

  public HighlighterClient getClient() {
    return myEditor;
  }

  protected final synchronized void resetText(@NotNull CharSequence text) {
    myText = null;
    doSetText(text);
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    synchronized (this) {
      doSetText(text);
    }
  }

  protected class TokenProcessor {
    public void addToken(final int i, final int startOffset, final int endOffset, final int data, final IElementType tokenType) {
      mySegments.setElementAt(i, startOffset, endOffset, data);
    }

    public void finish() {
    }
  }

  private void doSetText(final CharSequence text) {
    if (Comparing.equal(myText, text)) return;
    myText = ImmutableCharSequence.asImmutable(text);

    final TokenProcessor processor = createTokenProcessor(0);
    final int textLength = text.length();
    myLexer.start(text, 0, textLength, myInitialState);
    mySegments.removeAll();
    int i = 0;
    while (true) {
      final IElementType tokenType = myLexer.getTokenType();
      if (tokenType == null) break;

      int data = packData(tokenType, myLexer.getState());
      processor.addToken(i, myLexer.getTokenStart(), myLexer.getTokenEnd(), data, tokenType);
      i++;
      myLexer.advance();
    }
    processor.finish();
    
    if (textLength > 0 && (mySegments.mySegmentCount == 0 || mySegments.myEnds[mySegments.mySegmentCount - 1] != textLength)) {
      throw new IllegalStateException("Unexpected termination offset for lexer " + myLexer);
    }

    if(myEditor != null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      UIUtil.invokeLaterIfNeeded((DumbAwareRunnable)() -> myEditor.repaint(0, textLength));
    }
  }

  protected TokenProcessor createTokenProcessor(final int startIndex) {
    return new TokenProcessor();
  }

  public SyntaxHighlighter getSyntaxHighlighter() {
    return myHighlighter;
  }

  @NotNull
  private TextAttributes getAttributes(IElementType tokenType) {
    TextAttributes attrs = myAttributesMap.get(tokenType);
    if (attrs == null) {
      // let's fetch syntax highlighter attributes for token and merge them with "TEXT" attribute of current color scheme
      attrs = convertAttributes(myHighlighter.getTokenHighlights(tokenType));
      myAttributesMap.put(tokenType, attrs);
    }
    return attrs;
  }

  @NotNull
  public List<TextAttributes> getAttributesForPreviousAndTypedChars(@NotNull Document document, int offset, char c) {
    final CharSequence text = document.getImmutableCharSequence();

    final CharSequence newText = new MergingCharSequence(
      new MergingCharSequence(text.subSequence(0, offset), new SingleCharSequence(c)),
      text.subSequence(offset, text.length()));

    final List<IElementType> tokenTypes = getTokenType(newText, offset);

    return Arrays.asList(getAttributes(tokenTypes.get(0)).clone(), getAttributes(tokenTypes.get(1)).clone());
  }

  // TODO Unify with LexerEditorHighlighter.documentChanged
  @NotNull
  private List<IElementType> getTokenType(CharSequence text, int offset) {
    int startOffset = 0;

    int data = 0;
    int oldStartIndex = 0;
    int startIndex = 0;

    if (offset > 0 && mySegments.getSegmentCount() > 0) {
      final int segmentIndex = mySegments.findSegmentIndex(offset - 1) - 2;
      oldStartIndex = Math.max(0, segmentIndex);
      startIndex = oldStartIndex;

      do {
        data = mySegments.getSegmentData(startIndex);
        if (isInitialState(data)|| startIndex == 0) break;
        startIndex--;
      }
      while (true);

      startOffset = mySegments.getSegmentStart(startIndex);
    }

    myLexer.start(text, startOffset, text.length(), myInitialState);

    while (myLexer.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = myLexer.getTokenStart();
      int lexerState = myLexer.getState();

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), lexerState);
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      myLexer.advance();
    }

    IElementType tokenType1 = null;
    IElementType tokenType2 = null;

    while (myLexer.getTokenType() != null) {
      int lexerState = myLexer.getState();
      data = packData(myLexer.getTokenType(), lexerState);
      if (tokenType1 == null && myLexer.getTokenEnd() >= offset) {
        tokenType1 = unpackToken(data);
      }
      if (myLexer.getTokenEnd() >= offset + 1) {
        tokenType2 = unpackToken(data);
        break;
      }
      myLexer.advance();
    }

    return Arrays.asList(tokenType1, tokenType2);
  }

  @NotNull
  TextAttributes convertAttributes(@NotNull TextAttributesKey[] keys) {
    TextAttributes attrs = new TextAttributes();
    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = myScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }
    return attrs;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" +
           (myLexer.getClass() == FlexAdapter.class ? myLexer.toString() : myLexer.getClass().getName()) +
           "): '" + myLexer.getBufferSequence() + "'";
  }

  public class HighlighterIteratorImpl implements HighlighterIterator {
    private int mySegmentIndex = 0;

    HighlighterIteratorImpl(int startOffset) {
      try {
        mySegmentIndex = mySegments.findSegmentIndex(startOffset);
      }
      catch (IllegalStateException e) {
        throw new InvalidStateException(LexerEditorHighlighter.this, "wrong state", e);
      }
    }

    public int currentIndex() {
      return mySegmentIndex;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return getAttributes(getTokenType());
    }

    @Override
    public int getStart() {
      return mySegments.getSegmentStart(mySegmentIndex);
    }

    @Override
    public int getEnd() {
      return mySegments.getSegmentEnd(mySegmentIndex);
    }

    @Override
    public IElementType getTokenType(){
      return unpackToken(mySegments.getSegmentData(mySegmentIndex));
    }

    @Override
    public void advance() {
      mySegmentIndex++;
    }

    @Override
    public void retreat(){
      mySegmentIndex--;
    }

    @Override
    public boolean atEnd() {
      return mySegmentIndex >= mySegments.getSegmentCount() || mySegmentIndex < 0;
    }

    @Override
    public Document getDocument() {
      return LexerEditorHighlighter.this.getDocument();
    }
  }

  public SegmentArrayWithData getSegments() {
    return mySegments;
  }

  public static class InvalidStateException extends RuntimeException implements ExceptionWithAttachments {
    private final Attachment[] myAttachments;

    private InvalidStateException(LexerEditorHighlighter highlighter, String message, Throwable cause) {
      super(highlighter.getClass().getName() + "(" +
            (highlighter.myLexer.getClass() == FlexAdapter.class ? highlighter.myLexer.toString()
                                                                 : highlighter.myLexer.getClass().getName()) +
            "): " + message,
            cause);
      myAttachments = new Attachment[] {new Attachment("content.txt", highlighter.myLexer.getBufferSequence().toString())};
    }

    @NotNull
    @Override
    public Attachment[] getAttachments() {
      return myAttachments;
    }
  }
}

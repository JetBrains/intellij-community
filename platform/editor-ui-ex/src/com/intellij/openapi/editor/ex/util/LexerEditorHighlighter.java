// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.RestartableLexer;
import com.intellij.lexer.TokenIterator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.ImmutableCharSequence;
import com.intellij.util.text.SingleCharSequence;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;

public class LexerEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance(LexerEditorHighlighter.class);
  private static final int LEXER_INCREMENTALITY_THRESHOLD = 200;
  private static final Set<Class<?>> ourNonIncrementalLexers = new HashSet<>();
  private HighlighterClient myEditor;
  private final Lexer myLexer;
  private final Map<IElementType, TextAttributes> myAttributesMap = new HashMap<>();
  private final Map<IElementType, TextAttributesKey[]> myKeysMap = new HashMap<>();
  private SegmentArrayWithData mySegments;
  private final SyntaxHighlighter myHighlighter;
  private @NotNull EditorColorsScheme myScheme;
  private final int myInitialState;
  protected CharSequence myText;

  public LexerEditorHighlighter(@NotNull SyntaxHighlighter highlighter, @NotNull EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myLexer.start(Strings.EMPTY_CHAR_SEQUENCE);
    myInitialState = myLexer.getState();
    myHighlighter = highlighter;
    mySegments = createSegments();
  }

  protected @NotNull SegmentArrayWithData createSegments() {
    return new SegmentArrayWithData(createStorage());
  }

  /**
   * Defines how to pack/unpack and store highlighting states.
   * <p>
   * By default a editor highlighter uses {@link ShortBasedStorage} implementation which
   * serializes information about element type to be highlighted with elements' indices ({@link IElementType#getIndex()})
   * and deserializes ids back to {@link IElementType} using element types registry {@link IElementType#find(short)}.
   * <p>
   * If you need to store more information during syntax highlighting or
   * if your element types cannot be restored from {@link IElementType#getIndex()},
   * you can implement you own storage and override this method.
   * <p>
   * As an example, see {@link org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer},
   * that lexes files with unregistered (without index) element types and its
   * data storage ({@link org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage}
   * serializes/deserializes them to/from strings. The storage is created in {@link org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateEditorHighlighterProvider.TextMateLexerEditorHighlighter}
   *
   * @return data storage for highlighter states
   */
  protected @NotNull DataStorage createStorage() {
    return myLexer instanceof RestartableLexer ? new IntBasedStorage() : new ShortBasedStorage();
  }

  public boolean isPlain() {
    return myHighlighter instanceof PlainSyntaxHighlighter;
  }

  protected final @Nullable Document getDocument() {
    return myEditor != null ? myEditor.getDocument() : null;
  }

  public final synchronized boolean checkContentIsEqualTo(@NotNull CharSequence sequence) {
    Document document = getDocument();
    return document != null && isInSyncWithDocument() && Comparing.equal(document.getImmutableCharSequence(), sequence);
  }

  public @NotNull EditorColorsScheme getScheme() {
    return myScheme;
  }

  protected @NotNull Lexer getLexer() {
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

  @Override
  public @NotNull HighlighterIterator createIterator(int startOffset) {
    synchronized (this) {
      if (!isInSyncWithDocument()) {
        Document document = getDocument();
        assert document != null;
        if (document.isInBulkUpdate()) {
          document.setInBulkUpdate(false); // bulk mode failed
        }
        doSetText(document.getImmutableCharSequence());
      }

      try {
        int latestValidOffset = mySegments.getLastValidOffset();
        return new HighlighterIteratorImpl(Math.max(0, Math.min(startOffset, latestValidOffset)));
      }
      catch (CancellationException e) {
        throw e;
      }
      catch (Throwable t) {
        if (t instanceof ControlFlowException) throw t;

        LOG.error("Error creating highlighter iterator at offset " + startOffset + " in " + this, t);
        return new EmptyEditorHighlighter().createIterator(startOffset);
      }
    }
  }

  public boolean isValid() {
    Project project = myEditor.getProject();
    return project != null && !project.isDisposed();
  }

  private boolean isInSyncWithDocument() {
    Document document = getDocument();
    return document == null || document.getTextLength() == 0 || mySegments.getSegmentCount() > 0;
  }

  private boolean isInitialState(int data) {
    if (myLexer instanceof RestartableLexer) {
      int state = mySegments.unpackStateFromData(data);
      return ((RestartableLexer)myLexer).isRestartableState(state);
    }
    else {
      return data >= 0;
    }
  }

  /**
   * @return last updated offset - used by LazyLexerEditorHighlighter to optimize bulk updates
   */
  int incrementalUpdate(int eventOffset, int eventOldLength, int eventNewLength, @NotNull Document document) {
    CharSequence text = document.getImmutableCharSequence();
    if (mySegments.getSegmentCount() == 0 || mySegments.getLastValidOffset() < eventOffset) {
      setText(text);
      return text.length();
    }
    myText = text;

    int segmentIndex = mySegments.findSegmentIndex(eventOffset) - 2;
    int oldStartIndex = Math.max(0, segmentIndex);
    int startIndex = oldStartIndex;

    int data;
    do {
      data = mySegments.getSegmentData(startIndex);
      if (isInitialState(data) || startIndex == 0) break;
      startIndex--;
    }
    while (true);

    int startOffset = mySegments.getSegmentStart(startIndex);

    int initialState;
    int textLength = text.length();
    if (startOffset == 0 && myLexer instanceof RestartableLexer) {
      initialState = ((RestartableLexer)myLexer).getStartState();
      myLexer.start(text, startOffset, text.length(), initialState);
    }
    else {
      if (myLexer instanceof RestartableLexer) {
        initialState = mySegments.unpackStateFromData(mySegments.getSegmentData(startIndex));
        ((RestartableLexer)myLexer).start(text, startOffset, text.length(), initialState, createTokenIterator(startIndex));
      }
      else {
        initialState = myInitialState;
        myLexer.start(text, startOffset, text.length(), initialState);
      }
    }

    Lexer lexerWrapper = new ValidatingLexerWrapper(myLexer);
    for (IElementType tokenType = lexerWrapper.getTokenType(); tokenType != null; tokenType = lexerWrapper.getTokenType()) {
      if (startIndex >= oldStartIndex) break;

      int lexerState = lexerWrapper.getState();
      int tokenStart = lexerWrapper.getTokenStart();
      int tokenEnd = lexerWrapper.getTokenEnd();

      data = mySegments.packData(tokenType, lexerState, canRestart(lexerState));
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      lexerWrapper.advance();
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
    SegmentArrayWithData insertSegments = new SegmentArrayWithData(mySegments.createStorage());

    int repaintEnd = -1;
    int insertSegmentCount = 0;
    int oldEndIndex = -1;
    int shift = eventNewLength - eventOldLength;
    int newEndOffset = eventOffset + eventNewLength;
    int lastSegmentOffset = mySegments.getLastValidOffset();
    for (IElementType tokenType = lexerWrapper.getTokenType(); tokenType != null; tokenType = lexerWrapper.getTokenType()) {
      int lexerState = lexerWrapper.getState();
      int tokenStart = lexerWrapper.getTokenStart();
      int tokenEnd = lexerWrapper.getTokenEnd();

      data = mySegments.packData(tokenType, lexerState, canRestart(lexerState));
      int shiftedTokenStart = tokenStart - shift;
      if (tokenStart >= newEndOffset && shiftedTokenStart < lastSegmentOffset && canRestart(lexerState)) {
        int index = mySegments.findSegmentIndex(shiftedTokenStart);
        if (mySegments.getSegmentStart(index) == shiftedTokenStart && mySegments.getSegmentData(index) == data) {
          repaintEnd = tokenStart;
          oldEndIndex = index;
          break;
        }
      }
      insertSegments.setElementAt(insertSegmentCount, tokenStart, tokenEnd, data);
      insertSegmentCount++;
      lexerWrapper.advance();
    }

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

    if (repaintEnd == -1) {
      repaintEnd = textLength;
    }

    if (oldEndIndex < 0) {
      oldEndIndex = mySegments.getSegmentCount();
    }
    mySegments.shiftSegments(oldEndIndex, shift);
    mySegments.replace(startIndex, oldEndIndex, insertSegments);

    if (insertSegmentCount != 0 &&
        (oldEndIndex != startIndex + 1 || insertSegmentCount != 1 || data != mySegments.getSegmentData(startIndex))) {
      myEditor.repaint(startOffset, repaintEnd);
    }
    return repaintEnd;
  }

  @Override
  public synchronized void documentChanged(@NotNull DocumentEvent e) {
    try {
      Document document = e.getDocument();

      if (document.isInBulkUpdate()) {
        myText = null;
        mySegments.removeAll();
        return;
      }
      incrementalUpdate(e.getOffset(), e.getOldLength(), e.getNewLength(), document);
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

  private @NotNull TokenIterator createTokenIterator(int start) {
    return new TokenIterator() {
      @Override
      public int getStartOffset(int index) {
        return mySegments.getSegmentStart(index);
      }

      @Override
      public int getEndOffset(int index) {
        return mySegments.getSegmentEnd(index);
      }

      @Override
      public @NotNull IElementType getType(int index) {
        return mySegments.unpackTokenFromData(mySegments.getSegmentData(index));
      }

      @Override
      public int getState(int index) {
        return mySegments.unpackStateFromData(mySegments.getSegmentData(index));
      }

      @Override
      public int getTokenCount() {
        return mySegments.getSegmentCount();
      }

      @Override
      public int initialTokenIndex() {
        return start;
      }
    };
  }

  private boolean canRestart(int lexerState) {
    if (myLexer instanceof RestartableLexer) {
      return ((RestartableLexer)myLexer).isRestartableState(lexerState);
    }
    return lexerState == myInitialState;
  }

  protected boolean hasAdditionalData(int segmentIndex) {
    return false;
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.LEXER_EDITOR;
  }

  private static boolean segmentsEqual(@NotNull SegmentArrayWithData a1,
                                       int idx1,
                                       @NotNull SegmentArrayWithData a2,
                                       int idx2,
                                       int offsetShift) {
    return a1.getSegmentStart(idx1) + offsetShift == a2.getSegmentStart(idx2) &&
           a1.getSegmentEnd(idx1) + offsetShift == a2.getSegmentEnd(idx2) &&
           a1.getSegmentData(idx1) == a2.getSegmentData(idx2);
  }

  public HighlighterClient getClient() {
    return myEditor;
  }

  final synchronized void resetText(@NotNull CharSequence text) {
    myText = null;
    doSetText(text);
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    synchronized (this) {
      doSetText(text);
    }
  }

  protected interface TokenProcessor {
    void addToken(int tokenIndex, int startOffset, int endOffset, int data, @NotNull IElementType tokenType);

    default void finish() {}
  }

  private void doSetText(@NotNull CharSequence text) {
    if (Comparing.equal(myText, text)) return;
    text = ImmutableCharSequence.asImmutable(text);

    SegmentArrayWithData tempSegments = createSegments();
    TokenProcessor processor = createTokenProcessor(0, tempSegments, text);
    int textLength = text.length();
    Lexer lexerWrapper = new ValidatingLexerWrapper(myLexer);

    lexerWrapper.start(text, 0, textLength,
                       myLexer instanceof RestartableLexer ? ((RestartableLexer)myLexer).getStartState() : myInitialState);
    int i = 0;
    while (true) {
      IElementType tokenType = lexerWrapper.getTokenType();
      if (tokenType == null) break;

      int state = lexerWrapper.getState();
      int data = tempSegments.packData(tokenType, state, canRestart(state));
      processor.addToken(i, lexerWrapper.getTokenStart(), lexerWrapper.getTokenEnd(), data, tokenType);
      i++;
      if (i % 1024 == 0) {
        ProgressManager.checkCanceled();
      }
      lexerWrapper.advance();
    }

    myText = text;
    mySegments = tempSegments;
    processor.finish();

    if (textLength > 0 && (mySegments.getSegmentCount() == 0 || mySegments.getSegmentEnd(mySegments.getSegmentCount() - 1) != textLength)) {
      throw new IllegalStateException("Unexpected termination offset for lexer " + myLexer);
    }

    if (myEditor != null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      UIUtil.invokeLaterIfNeeded(() -> myEditor.repaint(0, textLength));
    }
  }

  protected @NotNull TokenProcessor createTokenProcessor(int startIndex, @NotNull SegmentArrayWithData segments, @NotNull CharSequence myText) {
    return (tokenIndex, startOffset, endOffset, data, tokenType) -> segments.setElementAt(tokenIndex, startOffset, endOffset, data);
  }

  public @NotNull SyntaxHighlighter getSyntaxHighlighter() {
    return myHighlighter;
  }

  private @NotNull TextAttributes getAttributes(@NotNull IElementType tokenType) {
    TextAttributes attrs = myAttributesMap.get(tokenType);
    if (attrs == null) {
      // let's fetch syntax highlighter attributes for token and merge them with "TEXT" attribute of current color scheme
      attrs = convertAttributes(getAttributesKeys(tokenType));
      myAttributesMap.put(tokenType, attrs);
    }
    return attrs;
  }

  private TextAttributesKey @NotNull [] getAttributesKeys(@NotNull IElementType tokenType) {
    TextAttributesKey[] attributesKeys = myKeysMap.get(tokenType);
    if (attributesKeys == null) {
      attributesKeys = myHighlighter.getTokenHighlights(tokenType);
      myKeysMap.put(tokenType, attributesKeys);
    }
    return attributesKeys;
  }

  public @NotNull List<TextAttributes> getAttributesForPreviousAndTypedChars(@NotNull Document document, int offset, char c) {
    CharSequence text = document.getImmutableCharSequence();

    CharSequence newText = StringUtil.replaceSubSequence(text, offset, offset, new SingleCharSequence(c));

    List<IElementType> tokenTypes = getTokenType(newText, offset);

    return Arrays.asList(getAttributes(tokenTypes.get(0)).clone(), getAttributes(tokenTypes.get(1)).clone());
  }

  // TODO Unify with LexerEditorHighlighter.documentChanged
  private @NotNull Lexer getLexerWrapper(@NotNull CharSequence text, int offset) {
    int startOffset = 0;

    int data = 0;
    boolean isDataSet = false;
    int oldStartIndex = 0;
    int startIndex = 0;

    if (offset > 0 && mySegments.getSegmentCount() > 0) {
      int segmentIndex = mySegments.findSegmentIndex(offset - 1) - 2;
      oldStartIndex = Math.max(0, segmentIndex);
      startIndex = oldStartIndex;

      do {
        data = mySegments.getSegmentData(startIndex);
        isDataSet = true;
        if (isInitialState(data) || startIndex == 0) break;
        startIndex--;
      }
      while (true);

      startOffset = mySegments.getSegmentStart(startIndex);
    }

    int state;
    if (myLexer instanceof RestartableLexer) {
      if (isDataSet) {
        state = mySegments.unpackStateFromData(data);
      }
      else {
        state = ((RestartableLexer)myLexer).getStartState();
      }
    }
    else {
      state = myInitialState;
    }
    if (offset == 0 && myLexer instanceof RestartableLexer) {
      myLexer.start(text, startOffset, text.length(), ((RestartableLexer)myLexer).getStartState());
    }
    else {
      if (myLexer instanceof RestartableLexer) {
        ((RestartableLexer)myLexer).start(text, startOffset, text.length(), state, createTokenIterator(startIndex));
      }
      else {
        myLexer.start(text, startOffset, text.length(), state);
      }
    }

    Lexer lexerWrapper = new ValidatingLexerWrapper(myLexer);
    while (lexerWrapper.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = lexerWrapper.getTokenStart();
      int lexerState = lexerWrapper.getState();

      int tokenEnd = lexerWrapper.getTokenEnd();
      data = mySegments.packData(lexerWrapper.getTokenType(), lexerState, canRestart(lexerState));
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      lexerWrapper.advance();
    }

    return lexerWrapper;
  }

  private @NotNull List<IElementType> getTokenType(@NotNull CharSequence text, int offset) {
    var lexerWrapper = getLexerWrapper(text, offset);
    int data;

    IElementType tokenType1 = null;
    IElementType tokenType2 = null;

    while (lexerWrapper.getTokenType() != null) {
      int lexerState = lexerWrapper.getState();
      data = mySegments.packData(lexerWrapper.getTokenType(), lexerState, canRestart(lexerState));
      if (tokenType1 == null && lexerWrapper.getTokenEnd() >= offset) {
        tokenType1 = mySegments.unpackTokenFromData(data);
      }
      if (lexerWrapper.getTokenEnd() >= offset + 1) {
        tokenType2 = mySegments.unpackTokenFromData(data);
        break;
      }
      lexerWrapper.advance();
    }

    return Arrays.asList(tokenType1, tokenType2);
  }

  @ApiStatus.Internal
  public synchronized @NotNull List<Pair<TextRange, TextAttributes>> getAttributesFor(@NotNull Document document, int offset, @NotNull CharSequence s) {
    var lexerWrapper = getLexerWrapper(StringUtil.replaceSubSequence(document.getImmutableCharSequence(), offset, offset, s), offset);
    int data;

    List<Pair<TextRange, TextAttributes>> result = new ArrayList<>();

    while (lexerWrapper.getTokenType() != null) {
      int lexerState = lexerWrapper.getState();
      data = mySegments.packData(lexerWrapper.getTokenType(), lexerState, canRestart(lexerState));
      if (lexerWrapper.getTokenEnd() > offset) {
        int start = Math.max(offset, lexerWrapper.getTokenStart());
        int end = Math.min(lexerWrapper.getTokenEnd(), offset + s.length());
        TextAttributes attributes = getAttributes(mySegments.unpackTokenFromData(data));
        result.add(Pair.create(TextRange.create(start, end), attributes));
      }
      if (lexerWrapper.getTokenEnd() >= offset + s.length()) break;
      lexerWrapper.advance();
    }

    return result;
  }

  @NotNull
  TextAttributes convertAttributes(TextAttributesKey @NotNull [] keys) {
    return LayeredTextAttributes.create(myScheme, keys);
  }

  @Override
  public @NonNls String toString() {
    return getClass().getName() + "(" +
           (myLexer.getClass() == FlexAdapter.class ? myLexer.toString() : myLexer.getClass().getName()) +
           "): '" + myLexer.getBufferSequence() + "'";
  }

  public class HighlighterIteratorImpl implements HighlighterIterator {
    private int mySegmentIndex;

    HighlighterIteratorImpl(int startOffset) {
      if (startOffset < 0 || startOffset > mySegments.getLastValidOffset()) {
        throw new IllegalArgumentException("Invalid offset: " + startOffset + "; mySegments.getLastValidOffset()=" + mySegments.getLastValidOffset());
      }
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
    public TextAttributesKey @NotNull [] getTextAttributesKeys() {
      return getAttributesKeys(getTokenType());
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
    public IElementType getTokenType() {
      try {
        return mySegments.unpackTokenFromData(mySegments.getSegmentData(mySegmentIndex));
      }
      catch (IllegalStateException e) {
        throw new InvalidStateException(LexerEditorHighlighter.this, "wrong state", e);
      }
    }

    @Override
    public void advance() {
      mySegmentIndex++;
    }

    @Override
    public void retreat() {
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

    public HighlighterClient getClient() {
      return LexerEditorHighlighter.this.getClient();
    }
  }

  public @NotNull SegmentArrayWithData getSegments() {
    return mySegments;
  }

  public static final class InvalidStateException extends RuntimeException implements ExceptionWithAttachments {
    private final Attachment[] myAttachments;

    private InvalidStateException(LexerEditorHighlighter highlighter, String message, Throwable cause) {
      super(highlighter.getClass().getName() + "(" +
            (highlighter.myLexer.getClass() == FlexAdapter.class ? highlighter.myLexer.toString()
                                                                 : highlighter.myLexer.getClass().getName()) +
            "): " + message,
            cause);
      myAttachments = new Attachment[]{new Attachment("content.txt", highlighter.myLexer.getBufferSequence().toString())};
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      return myAttachments;
    }
  }
}

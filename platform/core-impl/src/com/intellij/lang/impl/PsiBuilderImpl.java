// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.BlockSupportImpl;
import com.intellij.psi.impl.DiffLog;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import com.intellij.util.ThreeState;
import com.intellij.util.TripleFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.lang.WhitespacesBinders.DEFAULT_RIGHT_BINDER;

public class PsiBuilderImpl extends UnprotectedUserDataHolder implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance(PsiBuilderImpl.class);
  private long myLexingTimeNs = 0;
  static PsiBuilderDiagnostics DIAGNOSTICS;

  // function stored in PsiBuilderImpl's user data that is called during reparse when the algorithm is not sure what to merge
  public static final Key<TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>>
    CUSTOM_COMPARATOR = Key.create("CUSTOM_COMPARATOR");

  private static final Key<TokenSequence> LAZY_PARSEABLE_TOKENS = Key.create("LAZY_PARSEABLE_TOKENS");

  private static TokenSet ourAnyLanguageWhitespaceTokens = TokenSet.EMPTY;

  private final Project myProject;
  private PsiFile myFile;

  private final int[] myLexStarts;
  private final IElementType[] myLexTypes;
  private int myCurrentLexeme;

  private final ParserDefinition myParserDefinition;
  private final Lexer myLexer;
  private final TokenSet myWhitespaces;
  private TokenSet myComments;

  private CharTable myCharTable;
  private final CharSequence myText;
  private final CharSequence myLastCommittedText;
  private final char[] myTextArray;
  private boolean myDebugMode;
  private final int myLexemeCount;
  private boolean myTokenTypeChecked;
  private ITokenTypeRemapper myRemapper;
  private WhitespaceSkippedCallback myWhitespaceSkippedCallback;

  private final ASTNode myOriginalTree;
  private final MyTreeStructure myParentLightTree;
  private final int myOffset;

  private final Interner<String> myErrorInterner = Interner.createStringInterner();

  private IElementType myCachedTokenType;

  private final Int2ObjectMap<LazyParseableToken> myChameleonCache = new Int2ObjectOpenHashMap<>();
  private final MarkerPool pool = new MarkerPool(this);
  private final MarkerOptionalData myOptionalData = new MarkerOptionalData();
  private final MarkerProduction myProduction = new MarkerProduction(pool, myOptionalData);

  public static void registerWhitespaceToken(@NotNull IElementType type) {
    ourAnyLanguageWhitespaceTokens = TokenSet.orSet(ourAnyLanguageWhitespaceTokens, TokenSet.create(type));
  }

  public PsiBuilderImpl(@Nullable Project project,
                        @Nullable PsiFile containingFile,
                        @NotNull ParserDefinition parserDefinition,
                        @NotNull Lexer lexer,
                        @Nullable CharTable charTable,
                        @NotNull CharSequence text,
                        @Nullable ASTNode originalTree,
                        @Nullable MyTreeStructure parentLightTree) {
    this(project, containingFile, parserDefinition, lexer, charTable, text, originalTree,
         originalTree == null ? null : originalTree.getText(), parentLightTree, null);
  }

  public PsiBuilderImpl(@NotNull Project project,
                        @NotNull ParserDefinition parserDefinition,
                        @NotNull Lexer lexer,
                        @NotNull ASTNode chameleon,
                        @NotNull CharSequence text) {
    this(project, SharedImplUtil.getContainingFile(chameleon), parserDefinition, lexer,
         SharedImplUtil.findCharTableByTree(chameleon), text,
         Pair.getFirst(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
         Pair.getSecond(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
         null, chameleon);
  }

  public PsiBuilderImpl(@NotNull Project project,
                        @NotNull ParserDefinition parserDefinition,
                        @NotNull Lexer lexer,
                        @NotNull LighterLazyParseableNode chameleon,
                        @NotNull CharSequence text) {
    this(project, chameleon.getContainingFile(), parserDefinition, lexer,
         chameleon.getCharTable(), text, null, null, ((LazyParseableToken)chameleon).myParentStructure, chameleon);
  }

  private PsiBuilderImpl(@Nullable Project project,
                         @Nullable PsiFile containingFile,
                         @NotNull ParserDefinition parserDefinition,
                         @NotNull Lexer lexer,
                         @Nullable CharTable charTable,
                         @NotNull CharSequence text,
                         @Nullable ASTNode originalTree,
                         @Nullable CharSequence lastCommittedText,
                         @Nullable MyTreeStructure parentLightTree,
                         @Nullable Object parentCachingNode) {
    myProject = project;
    myFile = containingFile;
    myParserDefinition = parserDefinition;

    myText = text;
    myTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    myLexer = lexer;

    myWhitespaces = parserDefinition.getWhitespaceTokens();
    myComments = parserDefinition.getCommentTokens();
    myCharTable = charTable;
    myOriginalTree = originalTree;
    myLastCommittedText = lastCommittedText;
    if ((originalTree == null) != (lastCommittedText == null)) {
      throw new IllegalArgumentException("originalTree and lastCommittedText must be null/notnull together but got: originalTree=" + originalTree + "; lastCommittedText=" +
                                         (lastCommittedText == null ? null : "'"+StringUtil.first(lastCommittedText, 80, true)+"'"));
    }
    myParentLightTree = parentLightTree;
    myOffset = parentCachingNode instanceof LazyParseableToken ? ((LazyParseableToken)parentCachingNode).getStartOffset() : 0;

    TokenSequence tokens = performLexing(parentCachingNode);
    myLexStarts = tokens.lexStarts;
    myLexTypes = tokens.lexTypes;
    myLexemeCount = tokens.lexemeCount;
    if (DIAGNOSTICS != null) {
      DIAGNOSTICS.registerPass(text.length(), myLexemeCount);
    }
  }

  private @NotNull TokenSequence performLexing(@Nullable Object parentCachingNode) {
    TokenSequence fromParent = null;

    if (parentCachingNode instanceof LazyParseableToken && shouldReuseCollapsedTokens(((LazyParseableToken)parentCachingNode).getTokenType())) {
      fromParent = ((LazyParseableToken)parentCachingNode).getParsedTokenSequence();
      assert fromParent == null || fromParent.lexStarts[fromParent.lexemeCount] == myText.length();
      ProgressIndicatorProvider.checkCanceled();
    }
    else if (parentCachingNode instanceof LazyParseableElement) {
      LazyParseableElement parentElement = (LazyParseableElement)parentCachingNode;
      fromParent = parentElement.getUserData(LAZY_PARSEABLE_TOKENS);
      parentElement.putUserData(LAZY_PARSEABLE_TOKENS, null);
    }

    if (fromParent != null) {
      if (doLexingOptimizationCorrectionCheck()) {
        fromParent.assertMatches(myText, myLexer);
      }
      return fromParent;
    }

    long startTime = System.nanoTime();
    try{
      return TokenSequence.performLexing(myText, myLexer);
    }
    finally {
      myLexingTimeNs = System.nanoTime() - startTime;
    }
  }

  private static boolean doLexingOptimizationCorrectionCheck() {
    return false; // set to true to check that re-lexing of chameleons produces the same sequence as cached one
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void enforceCommentTokens(@NotNull TokenSet tokens) {
    myComments = tokens;
  }

  @Override
  public @Nullable StartMarker getLatestDoneMarker() {
    int index = myProduction.size() - 1;
    while (index >= 0) {
      PsiBuilderImpl.StartMarker marker = myProduction.getDoneMarkerAt(index);
      if (marker != null) return marker;
      --index;
    }
    return null;
  }

  @Override
  public @NotNull List<ProductionMarker> getProductions() {
    return new AbstractList<ProductionMarker>() {
      @Override
      public ProductionMarker get(int index) {
        return myProduction.getMarkerAt(index);
      }

      @Override
      public int size() {
        return myProduction.size();
      }
    };
  }

  private interface Node extends LighterASTNode {
    boolean tokenTextMatches(@NotNull CharSequence chars);
  }

  public abstract static class ProductionMarker implements Node, Production {
    final int markerId;
    protected final PsiBuilderImpl myBuilder;
    protected int myLexemeIndex = -1;
    protected ProductionMarker myParent;
    protected ProductionMarker myNext;

    ProductionMarker(int markerId, @NotNull PsiBuilderImpl builder) {
      this.markerId = markerId;
      myBuilder = builder;
    }

    void clean() {
      myLexemeIndex = -1;
      myParent = myNext = null;
    }

    @Override
    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemeIndex] + myBuilder.myOffset;
    }

    @Override
    public boolean isCollapsed() {
      return myBuilder.myOptionalData.isCollapsed(markerId);
    }

    public void remapTokenType(@NotNull IElementType type) {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    @Override
    public int getStartIndex() {
      return myLexemeIndex;
    }

    @Override
    public int getEndIndex() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    abstract void setLexemeIndex(int lexemeIndex, boolean done);

    abstract int getLexemeIndex(boolean done);
  }

  static class StartMarker extends ProductionMarker implements Marker, LighterASTSyntaxTreeBuilderBackedNode {
    private IElementType myType;
    private int myDoneLexeme = -1;
    private ProductionMarker myFirstChild;
    private ProductionMarker myLastChild;

    StartMarker(int markerId, @NotNull PsiBuilderImpl builder) {
      super(markerId, builder);
    }

    @Override
    void clean() {
      super.clean();
      myBuilder.myOptionalData.clean(markerId);

      myType = null;
      myDoneLexeme = -1;
      myFirstChild = myLastChild = null;
    }

    @Override
    public boolean tokenTextMatches(@NotNull CharSequence chars) {
      if (myFirstChild != null) {
        throw new IllegalStateException("textMatches shouldn't be called on non-empty composite nodes");
      }
      return chars.length() == 0;
    }

    @Override
    public int getEndOffset() {
      return myBuilder.myLexStarts[getEndIndex()] + myBuilder.myOffset;
    }

    @Override
    public int getEndIndex() {
      return myDoneLexeme;
    }

    @Override
    public @Nullable String getErrorMessage() {
      return myType == TokenType.ERROR_ELEMENT ? myBuilder.myOptionalData.getDoneError(markerId) : null;
    }

    @Override
    void setLexemeIndex(int lexemeIndex, boolean done) {
      if (done) myDoneLexeme = lexemeIndex;
      else myLexemeIndex = lexemeIndex;
    }

    @Override
    int getLexemeIndex(boolean done) {
      return done ? myDoneLexeme : myLexemeIndex;
    }

    public void addChild(@NotNull ProductionMarker node) {
      if (myFirstChild == null) {
        myFirstChild = node;
      }
      else {
        myLastChild.myNext = node;
      }
      myLastChild = node;
    }

    @Override
    public @NotNull Marker precede() {
      return myBuilder.precede(this);
    }

    @Override
    public void drop() {
      myBuilder.myProduction.dropMarker(this);
    }

    @Override
    public void rollbackTo() {
      myBuilder.rollbackTo(this);
    }

    @Override
    public void done(@NotNull IElementType type) {
      if (type == TokenType.ERROR_ELEMENT) {
        LOG.warn("Error elements with empty message are discouraged. Please use builder.error() instead", new RuntimeException());
      }
      myType = type;
      myBuilder.processDone(this, null, null);
    }

    @Override
    public void collapse(@NotNull IElementType type) {
      done(type);
      myBuilder.myOptionalData.markCollapsed(markerId);
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
      if (type == TokenType.ERROR_ELEMENT) {
        LOG.warn("Error elements with empty message are discouraged. Please use builder.errorBefore() instead", new RuntimeException());
      }
      myType = type;
      myBuilder.processDone(this, null, (StartMarker)before);
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before, @NotNull @Nls String errorMessage) {
      StartMarker marker = (StartMarker)before;
      ErrorItem errorItem = myBuilder.pool.allocateErrorItem();
      errorItem.setMessage(errorMessage);
      errorItem.myLexemeIndex = marker.myLexemeIndex;
      myBuilder.myProduction.addBefore(errorItem, marker);
      doneBefore(type, before);
    }

    @Override
    public void error(@NotNull @Nls String message) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.processDone(this, message, null);
    }

    @Override
    public void errorBefore(@NotNull @Nls String message, @NotNull Marker before) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.processDone(this, message, (StartMarker)before);
    }

    @Override
    public IElementType getTokenType() {
      return myType;
    }

    @Override
    public void remapTokenType(@NotNull IElementType type) {
      //assert myType != null && type != null;
      myType = type;
    }

    @Override
    public void setCustomEdgeTokenBinders(WhitespacesAndCommentsBinder left, WhitespacesAndCommentsBinder right) {
      if (left != null) {
        myBuilder.myOptionalData.assignBinder(markerId, left, false);
      }
      if (right != null) {
        myBuilder.myOptionalData.assignBinder(markerId, right, true);
      }
    }

    @Override
    public String toString() {
      if (myLexemeIndex < 0) return "<dropped>";
      boolean isDone = isDone();
      CharSequence originalText = myBuilder.getOriginalText();
      int startOffset = getStartOffset() - myBuilder.myOffset;
      int endOffset = isDone ? getEndOffset() - myBuilder.myOffset : myBuilder.getCurrentOffset();
      CharSequence text = originalText.subSequence(startOffset, endOffset);
      return isDone ? text.toString() : text + "\u2026";
    }

    boolean isDone() {
      return myDoneLexeme != -1;
    }

    @Override
    public CharSequence getText() {
      if (!isDone()) return null;
      CharSequence originalText = myBuilder.getOriginalText();
      int startOffset = getStartOffset() - myBuilder.myOffset;
      int endOffset = getEndOffset() - myBuilder.myOffset;
      CharSequence text = originalText.subSequence(startOffset, endOffset);
      assert text.length() == getEndOffset() - getStartOffset();
      return text;
    }
  }

  private @NotNull Marker precede(@NotNull StartMarker marker) {
    assert marker.myLexemeIndex >= 0 : "Preceding disposed marker";
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker);
    }
    StartMarker pre = createMarker(marker.myLexemeIndex);
    myProduction.addBefore(pre, marker);
    return pre;
  }

  private abstract static class Token implements Node {
    StartMarker myParentNode;

    @Override
    public boolean tokenTextMatches(@NotNull CharSequence chars) {
      int start = getStartOffsetInBuilder();
      int end = getEndOffsetInBuilder();
      if (end - start != chars.length()) return false;

      PsiBuilderImpl builder = getBuilder();
      return builder.myTextArray != null ? CharArrayUtil.regionMatches(builder.myTextArray, start, end, chars)
                                         : CharArrayUtil.regionMatches(builder.myText, start, end, chars);
    }

    @Override
    public final int getEndOffset() {
      return getEndOffsetInBuilder() + getBuilder().myOffset;
    }

    @Override
    public final int getStartOffset() {
      return getStartOffsetInBuilder() + getBuilder().myOffset;
    }

    public final @NotNull CharSequence getText() {
      if (getTokenType() instanceof TokenWrapper) {
        return ((TokenWrapper)getTokenType()).getValue();
      }

      return getBuilder().myText.subSequence(getStartOffsetInBuilder(), getEndOffsetInBuilder());
    }

    @NotNull
    PsiBuilderImpl getBuilder() {
      return myParentNode.myBuilder;
    }

    abstract int getStartOffsetInBuilder();
    abstract int getEndOffsetInBuilder();

    void clean() {
      myParentNode = null;
    }

    @Override
    public String toString() {
      return getText().toString();
    }
  }

  private abstract static class TokenRange extends Token {
    private int myTokenStart;
    private int myTokenEnd;
    private IElementType myTokenType;

    @Override
    int getStartOffsetInBuilder() {
      return myTokenStart;
    }

    @Override
    int getEndOffsetInBuilder() {
      return myTokenEnd;
    }

    @Override
    public IElementType getTokenType() {
      return myTokenType;
    }

    void initToken(@NotNull IElementType type, @NotNull StartMarker parent, int start, int end) {
      myParentNode = parent;
      myTokenType = type;
      myTokenStart = start;
      myTokenEnd = end;
    }
  }

  private static class TokenRangeNode extends TokenRange implements LighterASTTokenNode { }

  private static class SingleLexemeNode extends Token implements LighterASTTokenNode {
    private int myLexemeIndex;

    @Override
    int getStartOffsetInBuilder() {
      return getBuilder().myLexStarts[myLexemeIndex];
    }

    @Override
    int getEndOffsetInBuilder() {
      return getBuilder().myLexStarts[myLexemeIndex + 1];
    }

    @Override
    public @NotNull IElementType getTokenType() {
      return getBuilder().myLexTypes[myLexemeIndex];
    }
  }

  private static class LazyParseableToken extends TokenRange implements LighterLazyParseableNode {
    private final MyTreeStructure myParentStructure;
    private final int myStartIndex;
    private final int myEndIndex;
    private FlyweightCapableTreeStructure<LighterASTNode> myParsed;

    LazyParseableToken(@NotNull MyTreeStructure parentStructure, int startIndex, int endIndex) {
      myParentStructure = parentStructure;
      myStartIndex = startIndex;
      myEndIndex = endIndex;
    }

    @Override
    public PsiFile getContainingFile() {
      return getBuilder().myFile;
    }

    @Override
    public CharTable getCharTable() {
      return getBuilder().myCharTable;
    }

    public @NotNull FlyweightCapableTreeStructure<LighterASTNode> parseContents() {
      FlyweightCapableTreeStructure<LighterASTNode> parsed = myParsed;
      if (parsed == null) {
        myParsed = parsed = ((ILightLazyParseableElementType)getTokenType()).parseContents(this);
      }
      return parsed;
    }

    @Override
    public boolean accept(@NotNull Visitor visitor) {
      for (int i = myStartIndex; i < myEndIndex; i++) {
        IElementType type = getBuilder().myLexTypes[i];
        if (!visitor.visit(type)) {
          return false;
        }
      }

      return true;
    }

    private @Nullable TokenSequence getParsedTokenSequence() {
      int tokenCount = myEndIndex - myStartIndex;
      if (tokenCount == 1) return null; // not expand single lazy parseable token case

      int[] lexStarts = new int[tokenCount + 1];
      System.arraycopy(getBuilder().myLexStarts, myStartIndex, lexStarts, 0, tokenCount);
      int diff = getBuilder().myLexStarts[myStartIndex];
      for(int i = 0; i < tokenCount; ++i) {
        lexStarts[i] -= diff;
      }
      lexStarts[tokenCount] = getEndOffset() - getStartOffset();

      IElementType[] lexTypes = new IElementType[tokenCount + 1];
      System.arraycopy(getBuilder().myLexTypes, myStartIndex, lexTypes, 0, tokenCount);

      return new TokenSequence(lexStarts, lexTypes, tokenCount, getText());
    }
  }

  static class ErrorItem extends ProductionMarker {
    private @NlsContexts.DetailedDescription String myMessage;

    ErrorItem(int markerId, @NotNull PsiBuilderImpl builder) {
      super(markerId, builder);
    }

    @Override
    void clean() {
      super.clean();
      myMessage = null;
    }

    void setMessage(@NlsContexts.DetailedDescription String message) {
      myMessage = myBuilder.myErrorInterner.intern(message);
    }

    @Override
    void setLexemeIndex(int lexemeIndex, boolean done) {
      assert !done;
      myLexemeIndex = lexemeIndex;
    }

    @Override
    int getLexemeIndex(boolean done) {
      assert !done;
      return myLexemeIndex;
    }

    @Override
    public boolean tokenTextMatches(@NotNull CharSequence chars) {
      return chars.length() == 0;
    }

    @Override
    public int getEndOffset() {
      return getStartOffset();
    }

    @Override
    public int getEndIndex() {
      return getStartIndex();
    }

    @Override
    public @Nullable String getErrorMessage() {
      return myMessage;
    }

    @Override
    public @NotNull IElementType getTokenType() {
      return TokenType.ERROR_ELEMENT;
    }
  }

  @Override
  public @NotNull CharSequence getOriginalText() {
    return myText;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    IElementType cached = myCachedTokenType;
    if (cached == null) {
      myCachedTokenType = cached = calcTokenType();
    }
    return cached;
  }

  @Override
  public boolean isWhitespaceOrComment(@NotNull IElementType elementType) {
    return myWhitespaces.contains(elementType) || myComments.contains(elementType);
  }

  private void clearCachedTokenType() {
    myCachedTokenType = null;
  }

  private IElementType remapCurrentToken() {
    if (myCachedTokenType != null) return myCachedTokenType;
    if (myRemapper != null) {
      remapCurrentToken(myRemapper.filter(myLexTypes[myCurrentLexeme], myLexStarts[myCurrentLexeme],
                                          myLexStarts[myCurrentLexeme + 1], myText));
    }
    return myLexTypes[myCurrentLexeme];
  }

  private IElementType calcTokenType() {
    if (eof()) return null;

    if (myRemapper != null) {
      //remaps current token, and following, which remaps to spaces and comments
      skipWhitespace();
    }
    return myLexTypes[myCurrentLexeme];
  }

  @Override
  public void setTokenTypeRemapper(ITokenTypeRemapper remapper) {
    myRemapper = remapper;
    myTokenTypeChecked = false;
    clearCachedTokenType();
  }

  @Override
  public void remapCurrentToken(@NotNull IElementType type) {
    myLexTypes[myCurrentLexeme] = type;
    clearCachedTokenType();
  }

  @Override
  public @Nullable IElementType lookAhead(int steps) {
    int cur = shiftOverWhitespaceForward(myCurrentLexeme);

    while (steps > 0) {
      cur = shiftOverWhitespaceForward(cur + 1);
      steps--;
    }

    return cur < myLexemeCount ? myLexTypes[cur] : null;
  }

  private int shiftOverWhitespaceForward(int lexIndex) {
    while (lexIndex < myLexemeCount && isWhitespaceOrComment(myLexTypes[lexIndex])) {
      lexIndex++;
    }
    return lexIndex;
  }

  @Override
  public IElementType rawLookup(int steps) {
    int cur = myCurrentLexeme + steps;
    return cur < myLexemeCount && cur >= 0 ? myLexTypes[cur] : null;
  }

  @Override
  public int rawTokenTypeStart(int steps) {
    int cur = myCurrentLexeme + steps;
    if (cur < 0) return -1;
    if (cur >= myLexemeCount) return getOriginalText().length();
    return myLexStarts[cur];
  }

  @Override
  public int rawTokenIndex() {
    return myCurrentLexeme;
  }

  @Override
  public void rawAdvanceLexer(int steps) {
    ProgressIndicatorProvider.checkCanceled();
    if (steps < 0) {
      throw new IllegalArgumentException("Steps must be a positive integer - lexer can only be advanced. " +
                                         "Use Marker.rollbackTo if you want to rollback PSI building.");
    }
    if (steps == 0) return;
    // Be permissive as advanceLexer() and don't throw error if advancing beyond eof state
    myCurrentLexeme += steps;
    if (myCurrentLexeme > myLexemeCount || myCurrentLexeme < 0 /* int overflow */ ) {
      myCurrentLexeme = myLexemeCount;
    }
    myTokenTypeChecked = false;
    clearCachedTokenType();
  }

  @Override
  public void setWhitespaceSkippedCallback(@Nullable WhitespaceSkippedCallback callback) {
    myWhitespaceSkippedCallback = callback;
  }

  @Override
  public void advanceLexer() {
    if ((myCurrentLexeme & 0xff) == 0) {
      ProgressIndicatorProvider.checkCanceled();
    }

    if (eof()) return;

    myTokenTypeChecked = false;
    myCurrentLexeme++;
    clearCachedTokenType();
  }

  private void skipWhitespace() {
    while (myCurrentLexeme < myLexemeCount && isWhitespaceOrComment(remapCurrentToken())) {
      onSkip(myLexTypes[myCurrentLexeme], myLexStarts[myCurrentLexeme], myCurrentLexeme + 1 < myLexemeCount ? myLexStarts[myCurrentLexeme + 1] : myText.length());
      myCurrentLexeme++;
      clearCachedTokenType();
    }
  }

  private void onSkip(IElementType type, int start, int end) {
    if (myWhitespaceSkippedCallback != null) {
      myWhitespaceSkippedCallback.onSkip(type, start, end);
    }
  }

  @Override
  public int getCurrentOffset() {
    if (eof()) return getOriginalText().length();
    return myLexStarts[myCurrentLexeme];
  }

  @Override
  public @Nullable String getTokenText() {
    if (eof()) return null;
    IElementType type = getTokenType();
    if (type instanceof TokenWrapper) {
      return ((TokenWrapper)type).getValue();
    }
    return myText.subSequence(myLexStarts[myCurrentLexeme], myLexStarts[myCurrentLexeme + 1]).toString();
  }

  @Deprecated
  public boolean whitespaceOrComment(IElementType token) {
    return isWhitespaceOrComment(token);
  }

  @Override
  public @NotNull Marker mark() {
    if (!myProduction.isEmpty()) {
      skipWhitespace();
    }

    StartMarker marker = createMarker(myCurrentLexeme);
    myProduction.addMarker(marker);
    return marker;
  }

  private @NotNull StartMarker createMarker(int lexemeIndex) {
    StartMarker marker = pool.allocateStartMarker();
    marker.myLexemeIndex = lexemeIndex;
    if (myDebugMode) {
      myOptionalData.notifyAllocated(marker.markerId);
    }
    return marker;
  }

  @Override
  public final boolean eof() {
    if (!myTokenTypeChecked) {
      myTokenTypeChecked = true;
      skipWhitespace();
    }
    return myCurrentLexeme >= myLexemeCount;
  }

  private void rollbackTo(@NotNull StartMarker marker) {
    assert marker.myLexemeIndex >= 0 : "The marker is already disposed";
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker);
    }
    if (DIAGNOSTICS != null) {
      DIAGNOSTICS.registerRollback(myCurrentLexeme - marker.myLexemeIndex);
    }
    myCurrentLexeme = marker.myLexemeIndex;
    myTokenTypeChecked = true;
    myProduction.rollbackTo(marker);
    clearCachedTokenType();
  }

  /**
   * @return true if there are error elements created and not dropped after marker was created
   */
  public boolean hasErrorsAfter(@NotNull Marker marker) {
    return myProduction.hasErrorsAfter((StartMarker)marker);
  }

  private void processDone(@NotNull StartMarker marker, @Nullable @Nls String errorMessage, @Nullable StartMarker before) {
    doValidityChecks(marker, before);

    if (errorMessage != null) {
      myOptionalData.setErrorMessage(marker.markerId, errorMessage);
    }

    int doneLexeme = before == null ? myCurrentLexeme : before.myLexemeIndex;
    if (marker.myType.isLeftBound() && isEmpty(marker.myLexemeIndex, doneLexeme)) {
      marker.setCustomEdgeTokenBinders(DEFAULT_RIGHT_BINDER, null);
    }
    marker.myDoneLexeme = doneLexeme;
    myProduction.addDone(marker, before);
  }

  private boolean isEmpty(int startIdx, int endIdx) {
    for (int i = startIdx; i < endIdx; i++) {
      IElementType token = myLexTypes[i];
      if (!isWhitespaceOrComment(token)) return false;
    }
    return true;
  }

  private void doValidityChecks(@NotNull StartMarker marker, @Nullable StartMarker before) {
    if (marker.isDone()) {
      LOG.error("Marker already done.");
    }

    if (myDebugMode) {
      myProduction.doHeavyChecksOnMarkerDone(marker, before);
    }
  }

  @Override
  public void error(@NotNull String messageText) {
    ProductionMarker lastMarker = myProduction.getStartMarkerAt(myProduction.size() - 1);
    if (lastMarker instanceof ErrorItem && lastMarker.myLexemeIndex == myCurrentLexeme) {
      return;
    }
    ErrorItem marker = pool.allocateErrorItem();
    marker.setMessage(messageText);
    marker.myLexemeIndex = myCurrentLexeme;
    myProduction.addMarker(marker);
  }

  @Override
  public @NotNull ASTNode getTreeBuilt() {
    return buildTree();
  }

  private @NotNull ASTNode buildTree() {
    StartMarker rootMarker = prepareLightTree();
    boolean possiblyTooDeep = myFile != null && BlockSupport.isTooDeep(myFile.getOriginalFile());

    if (myOriginalTree != null && !possiblyTooDeep) {
      DiffLog diffLog = merge(myOriginalTree, rootMarker, myLastCommittedText);
      throw new BlockSupport.ReparsedSuccessfullyException(diffLog);
    }

    TreeElement rootNode = createRootAST(rootMarker);
    bind(rootMarker, (CompositeElement)rootNode);

    if (possiblyTooDeep && !(rootNode instanceof FileElement)) {
      ASTNode childNode = rootNode.getFirstChildNode();
      if (childNode != null) {
        childNode.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
      }
    }

    if (LOG.isDebugEnabled() && rootNode.getTextLength() != myText.length()) {
      LOG.error("Inconsistent root node. " +
                "; node type: " + rootNode.getElementType() +
                "; text length: " + myText.length() +
                "; node length: " + rootNode.getTextLength() +
                "; partial text: " + StringUtil.shortenTextWithEllipsis(myText.toString(), 512, 256) +
                "; partial node text: " + StringUtil.shortenTextWithEllipsis(rootNode.getText(), 512, 256)
      );
    }

    return rootNode;
  }

  @Override
  public @NotNull FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    StartMarker rootMarker = prepareLightTree();
    return new MyTreeStructure(rootMarker, myParentLightTree);
  }

  private @NotNull TreeElement createRootAST(@NotNull StartMarker rootMarker) {
    IElementType type = rootMarker.getTokenType();
    TreeElement rootNode = type instanceof ILazyParseableElementType ?
                           createLazy((ILazyParseableElementType)type, null, getASTFactory()) :
                           createComposite(rootMarker, getASTFactory());
    if (myCharTable == null) {
      myCharTable = rootNode instanceof FileElement ? ((FileElement)rootNode).getCharTable() : new CharTableImpl();
    }
    if (!(rootNode instanceof FileElement)) {
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);
    }
    return rootNode;
  }

  private @Nullable ASTFactory getASTFactory() {
    return myParserDefinition instanceof ASTFactory ? (ASTFactory)myParserDefinition : null;
  }

  private static final class ConvertFromTokensToASTBuilder implements DiffTreeChangeBuilder<ASTNode, LighterASTNode> {
    private final DiffTreeChangeBuilder<? super ASTNode, ? super ASTNode> myDelegate;
    private final ASTConverter myConverter;

    private ConvertFromTokensToASTBuilder(@NotNull StartMarker rootNode,
                                          @NotNull DiffTreeChangeBuilder<? super ASTNode, ? super ASTNode> delegate) {
      myDelegate = delegate;
      myConverter = new ASTConverter(rootNode);
    }

    @Override
    public void nodeDeleted(@NotNull ASTNode oldParent, @NotNull ASTNode oldNode) {
      myDelegate.nodeDeleted(oldParent, oldNode);
    }

    @Override
    public void nodeInserted(@NotNull ASTNode oldParent, @NotNull LighterASTNode newNode, int pos) {
      myDelegate.nodeInserted(oldParent, myConverter.apply((Node)newNode), pos);
    }

    @Override
    public void nodeReplaced(@NotNull ASTNode oldChild, @NotNull LighterASTNode newChild) {
      ASTNode converted = myConverter.apply((Node)newChild);
      myDelegate.nodeReplaced(oldChild, converted);
    }
  }

  private static final @NonNls String UNBALANCED_MESSAGE =
    "Unbalanced tree. Most probably caused by unbalanced markers. " +
    "Try calling setDebugMode(true) against PsiBuilder passed to identify exact location of the problem";

  private @NotNull DiffLog merge(@NotNull ASTNode oldRoot, @NotNull StartMarker newRoot, @NotNull CharSequence lastCommittedText) {
    DiffLog diffLog = new DiffLog();
    DiffTreeChangeBuilder<ASTNode, LighterASTNode> builder = new ConvertFromTokensToASTBuilder(newRoot, diffLog);
    MyTreeStructure treeStructure = new MyTreeStructure(newRoot, null);
    List<CustomLanguageASTComparator> customLanguageASTComparators = CustomLanguageASTComparator.getMatchingComparators(myFile);
    ShallowNodeComparator<ASTNode, LighterASTNode> comparator =
      new MyComparator(treeStructure, customLanguageASTComparators, getUserData(CUSTOM_COMPARATOR));
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator == null) indicator = new EmptyProgressIndicator();
    BlockSupportImpl.diffTrees(oldRoot, builder, comparator, treeStructure, indicator, lastCommittedText);
    return diffLog;
  }

  private @NotNull StartMarker prepareLightTree() {
    if (myProduction.isEmpty()) {
      LOG.error("Parser produced no markers. Text:\n" + myText);
    }
    // build tree only once to avoid threading issues in read-only PSI
    StartMarker rootMarker = (StartMarker)Objects.requireNonNull(myProduction.getStartMarkerAt(0));
    if (rootMarker.myFirstChild != null) return rootMarker;

    myTokenTypeChecked = true;
    balanceWhiteSpaces();

    rootMarker.myParent = rootMarker.myFirstChild = rootMarker.myLastChild = rootMarker.myNext = null;
    StartMarker curNode = rootMarker;
    ArrayDeque<StartMarker> nodes = new ArrayDeque<>();
    nodes.addLast(rootMarker);

    int lastErrorIndex = -1;
    int maxDepth = 0;
    int curDepth = 0;
    boolean hasCollapsedChameleons = false;
    int[] productions = myProduction.elements();
    for (int i = 1, size = myProduction.size(); i < size; i++) {
      int id = productions[i];
      ProductionMarker item = id > 0 ? pool.get(id) : null;

      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        marker.myParent = curNode;
        marker.myFirstChild = marker.myLastChild = marker.myNext = null;
        curNode.addChild(marker);
        nodes.addLast(curNode);
        curNode = marker;
        curDepth++;
        if (curDepth > maxDepth) maxDepth = curDepth;
      }
      else if (item instanceof ErrorItem) {
        item.myParent = curNode;
        int curToken = item.myLexemeIndex;
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
      else {
        if (isCollapsedChameleon(curNode)) {
          hasCollapsedChameleons = true;
        }
        assertMarkersBalanced(id < 0 && pool.get(-id) == curNode, item);
        curNode = nodes.removeLast();
        curDepth--;
      }
    }

    if (myCurrentLexeme < myLexemeCount) {
      List<IElementType> missed = ContainerUtil.subArrayAsList(myLexTypes, myCurrentLexeme, myLexemeCount);
      LOG.error("Tokens " + missed + " were not inserted into the tree. "
                + (myFile == null
                   ? ""
                   : myFile.getLanguage()),
                new Attachment("missedTokensFragment.txt", myText.toString()));
    }

    if (rootMarker.getEndIndex() < myLexemeCount) {
      List<IElementType> missed = ContainerUtil.subArrayAsList(myLexTypes, rootMarker.getEndIndex(), myLexemeCount);
      LOG.error("Tokens " + missed + " are outside of root element \"" + rootMarker.myType + "\".",
                new Attachment("outsideTokensFragment.txt", myText.toString()));
    }

    assertMarkersBalanced(curNode == rootMarker, curNode);

    checkTreeDepth(maxDepth, rootMarker.getTokenType() instanceof IFileElementType, hasCollapsedChameleons);

    clearCachedTokenType();
    return rootMarker;
  }

  private static boolean isCollapsedChameleon(@NotNull StartMarker marker) {
    return marker.getTokenType() instanceof ILazyParseableElementTypeBase && marker.myFirstChild == null && marker.getTextLength() > 0;
  }

  private void assertMarkersBalanced(boolean condition, @Nullable ProductionMarker marker) {
    if (condition) return;

    reportUnbalancedMarkers(marker);
  }

  private void reportUnbalancedMarkers(@Nullable ProductionMarker marker) {
    int index = marker != null ? marker.getStartIndex() + 1 : myLexStarts.length;
    CharSequence context =
      index < myLexStarts.length ? myText.subSequence(Math.max(0, myLexStarts[index] - 1000), myLexStarts[index]) : "<none>";
    String language = myFile != null ? myFile.getLanguage() + ", " : "";
    LOG.error(UNBALANCED_MESSAGE + "\nlanguage: " + language + "\ncontext: '" + context + "'" +
              "\nmarker id: " + (marker == null ? "n/a" : marker.markerId));
  }

  private void balanceWhiteSpaces() {
    RelativeTokenTypesView wsTokens = new RelativeTokenTypesView();
    RelativeTokenTextView tokenTextGetter = new RelativeTokenTextView();
    int lastIndex = 0;

    int[] productions = myProduction.elements();
    for (int i = 1, size = myProduction.size() - 1; i < size; i++) {
      int id = productions[i];
      ProductionMarker starting = id > 0 ? pool.get(id) : null;
      if (starting instanceof StartMarker) {
        assertMarkersBalanced(((StartMarker)starting).isDone(), starting);
      }
      boolean done = starting == null;
      ProductionMarker item = starting != null ? starting : pool.get(-id);

      WhitespacesAndCommentsBinder binder;
      if (item instanceof ErrorItem) {
        assert !done;
        binder = DEFAULT_RIGHT_BINDER;
      }
      else {
        binder = myOptionalData.getBinder(item.markerId, done);
      }
      int lexemeIndex = item.getLexemeIndex(done);

      boolean recursive = binder instanceof WhitespacesAndCommentsBinder.RecursiveBinder;
      int prevProductionLexIndex;
      if (recursive) {
        prevProductionLexIndex = 0;
      }
      else {
        int prevId = productions[i - 1];
        prevProductionLexIndex = pool.get(Math.abs(prevId)).getLexemeIndex(prevId < 0);
      }
      int wsStartIndex = Math.max(lexemeIndex, lastIndex);
      while (wsStartIndex > prevProductionLexIndex && isWhitespaceOrComment(myLexTypes[wsStartIndex - 1])) wsStartIndex--;

      int wsEndIndex = shiftOverWhitespaceForward(lexemeIndex);

      if (wsStartIndex != wsEndIndex) {
        wsTokens.configure(wsStartIndex, wsEndIndex);
        tokenTextGetter.configure(wsStartIndex);
        boolean atEnd = wsStartIndex == 0 || wsEndIndex == myLexemeCount;
        lexemeIndex = wsStartIndex + binder.getEdgePosition(wsTokens, atEnd, tokenTextGetter);
        item.setLexemeIndex(lexemeIndex, done);
        if (recursive) {
          myProduction.confineMarkersToMaxLexeme(i, lexemeIndex);
        }
      }
      else if (lexemeIndex < wsStartIndex) {
        lexemeIndex = wsStartIndex;
        item.setLexemeIndex(wsStartIndex, done);
      }

      lastIndex = lexemeIndex;
    }
  }

  private final class RelativeTokenTypesView extends AbstractList<IElementType> {
    private int myStart;
    private int mySize;

    private void configure(int start, int end) {
      myStart = start;
      mySize = end - start;
    }

    @Override
    public IElementType get(int index) {
      return myLexTypes[myStart + index];
    }

    @Override
    public int size() {
      return mySize;
    }
  }

  private final class RelativeTokenTextView implements WhitespacesAndCommentsBinder.TokenTextGetter {
    private int myStart;

    private void configure(int start) {
      myStart = start;
    }

    @Override
    public @NotNull CharSequence get(int i) {
      return new CharSequenceSubSequence(myText, myLexStarts[myStart + i], myLexStarts[myStart + i + 1]);
    }
  }

  private void checkTreeDepth(int maxDepth, boolean isFileRoot, boolean hasCollapsedChameleons) {
    if (myFile == null) return;
    PsiFile file = myFile.getOriginalFile();
    Boolean flag = file.getUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED);
    if (maxDepth > BlockSupport.INCREMENTAL_REPARSE_DEPTH_LIMIT) {
      if (!Boolean.TRUE.equals(flag)) {
        file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
      }
    }
    else if (isFileRoot && flag != null && !hasCollapsedChameleons) {
      file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, null);
    }
  }

  private void bind(@NotNull StartMarker rootMarker, @NotNull CompositeElement rootNode) {
    ASTFactory astFactory = getASTFactory();
    StartMarker curMarker = rootMarker;
    CompositeElement curNode = rootNode;

    int lexIndex = rootMarker.myLexemeIndex;
    ProductionMarker item = rootMarker.myFirstChild != null ? rootMarker.myFirstChild : rootMarker;
    boolean itemDone = rootMarker.myFirstChild == null;
    while (true) {
      lexIndex = insertLeaves(lexIndex, item.getLexemeIndex(itemDone), curNode);

      if (item == rootMarker && itemDone) break;

      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        if (itemDone) {
          curMarker = (StartMarker)marker.myParent;
          curNode = curNode.getTreeParent();
          item = marker.myNext;
          itemDone = false;
        }
        else if (!marker.isCollapsed()) {
          curMarker = marker;

          CompositeElement childNode = createComposite(marker, astFactory);
          curNode.rawAddChildrenWithoutNotifications(childNode);
          curNode = childNode;

          item = marker.myFirstChild != null ? marker.myFirstChild : marker;
          itemDone = marker.myFirstChild == null;
          continue;
        }
        else {
          lexIndex = collapseLeaves(curNode, marker);
          item = marker.myNext;
        }
      }
      else if (item instanceof ErrorItem) {
        CompositeElement errorElement = Factory.createErrorElement(((ErrorItem)item).myMessage);
        curNode.rawAddChildrenWithoutNotifications(errorElement);
        item = item.myNext;
      }

      if (item == null) {
        item = curMarker;
        itemDone = true;
      }
    }
  }

  @Deprecated
  public boolean isCollapsed(@NotNull ProductionMarker marker) {
    return marker.isCollapsed();
  }

  private int insertLeaves(int curToken, int lastIdx, @NotNull CompositeElement curNode) {
    lastIdx = Math.min(lastIdx, myLexemeCount);
    while (curToken < lastIdx) {
      if ((curToken & 0xff) == 0) {
        ProgressIndicatorProvider.checkCanceled();
      }
      int start = myLexStarts[curToken];
      int end = myLexStarts[curToken + 1];
      if (start < end || myLexTypes[curToken] instanceof ILeafElementType) { // Empty token. Most probably a parser directive like indent/dedent in Python
        IElementType type = myLexTypes[curToken];
        TreeElement leaf = createLeaf(type, start, end);
        curNode.rawAddChildrenWithoutNotifications(leaf);
      }
      curToken++;
    }

    return curToken;
  }

  private int collapseLeaves(@NotNull CompositeElement ast, @NotNull StartMarker startMarker) {
    int start = myLexStarts[startMarker.myLexemeIndex];
    int end = myLexStarts[startMarker.getEndIndex()];
    IElementType markerType = startMarker.myType;
    TreeElement leaf = createLeaf(markerType, start, end);
    if (shouldReuseCollapsedTokens(markerType) &&
        startMarker.myLexemeIndex < startMarker.getEndIndex()) {
      int length = startMarker.getEndIndex() - startMarker.myLexemeIndex;
      int[] relativeStarts = new int[length + 1];
      IElementType[] types = new IElementType[length + 1];
      for (int i = startMarker.myLexemeIndex; i < startMarker.getEndIndex(); i++) {
        relativeStarts[i - startMarker.myLexemeIndex] = myLexStarts[i] - start;
        types[i - startMarker.myLexemeIndex] = myLexTypes[i];
      }
      relativeStarts[length] = end - start;
      leaf.putUserData(LAZY_PARSEABLE_TOKENS, new TokenSequence(relativeStarts, types, length, leaf.getChars()));
    }
    ast.rawAddChildrenWithoutNotifications(leaf);
    return startMarker.getEndIndex();
  }

  private static boolean shouldReuseCollapsedTokens(IElementType collapsed) {
    return collapsed instanceof ILazyParseableElementTypeBase && ((ILazyParseableElementTypeBase)collapsed).reuseCollapsedTokens();
  }

  private static @NotNull CompositeElement createComposite(@NotNull StartMarker marker, @Nullable ASTFactory astFactory) {
    IElementType type = marker.myType;
    if (type == TokenType.ERROR_ELEMENT) {
      String error = marker.myBuilder.myOptionalData.getDoneError(marker.markerId);
      Objects.requireNonNull(error);
      return Factory.createErrorElement(error);
    }

    if (type == null) {
      throw new RuntimeException(UNBALANCED_MESSAGE);
    }

    if (astFactory != null) {
      CompositeElement composite = astFactory.createComposite(marker.getTokenType());
      if (composite != null) return composite;
    }
    return ASTFactory.composite(type);
  }

  private static @NotNull LazyParseableElement createLazy(@NotNull ILazyParseableElementType type, @Nullable CharSequence text, @Nullable ASTFactory astFactory) {
    if (astFactory != null) {
      LazyParseableElement element = astFactory.createLazy(type, text);
      if (element != null) return element;
    }
    return ASTFactory.lazy(type, text);
  }

  public static @Nullable @NlsContexts.DetailedDescription String getErrorMessage(@NotNull LighterASTNode node) {
    return node instanceof Production ? ((Production)node).getErrorMessage() : null;
  }

  private static final class MyComparator implements ShallowNodeComparator<ASTNode, LighterASTNode> {
    private final TripleFunction<? super ASTNode, ? super LighterASTNode, ? super FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>
      myCustom;
    private final @NotNull List<? extends CustomLanguageASTComparator> myCustomLanguageASTComparators;
    private final MyTreeStructure myTreeStructure;

    private MyComparator(@NotNull MyTreeStructure treeStructure,
                         @NotNull List<? extends CustomLanguageASTComparator> customLanguageASTComparators,
                         @Nullable TripleFunction<? super ASTNode, ? super LighterASTNode, ? super FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> custom) {
      myCustom = custom;
      myCustomLanguageASTComparators = customLanguageASTComparators;
      myTreeStructure = treeStructure;
    }

    @Override
    public @NotNull ThreeState deepEqual(@NotNull ASTNode oldNode, @NotNull LighterASTNode newNode) {
      ProgressIndicatorProvider.checkCanceled();

      boolean oldIsErrorElement = oldNode instanceof PsiErrorElement && oldNode.getElementType() == TokenType.ERROR_ELEMENT;
      boolean newIsErrorElement = newNode.getTokenType() == TokenType.ERROR_ELEMENT;
      if (oldIsErrorElement != newIsErrorElement) return ThreeState.NO;
      if (oldIsErrorElement) {
        PsiErrorElement e1 = (PsiErrorElement)oldNode;
        return Objects.equals(e1.getErrorDescription(), getErrorMessage(newNode)) ? ThreeState.UNSURE : ThreeState.NO;
      }

      ThreeState customResult = customCompare(oldNode, newNode);
      if (customResult != ThreeState.UNSURE) {
        return customResult;
      }
      if (newNode instanceof Token) {
        IElementType type = newNode.getTokenType();
        Token token = (Token)newNode;

        if (oldNode instanceof ForeignLeafPsiElement) {
          return type instanceof ForeignLeafType && ((ForeignLeafType)type).getValue().equals(oldNode.getText())
                 ? ThreeState.YES
                 : ThreeState.NO;
        }

        if (oldNode instanceof LeafElement) {
          if (type instanceof ForeignLeafType) return ThreeState.NO;

          return ((LeafElement)oldNode).textMatches(token.getText())
                 ? ThreeState.YES
                 : ThreeState.NO;
        }

        if (type instanceof ILightLazyParseableElementType) {
          if (((TreeElement)oldNode).textMatches(token.getText())) {
            return PsiDocumentManagerBase.isFullReparseInProgress() ? ThreeState.UNSURE : ThreeState.YES;
          }
          return TreeUtil.isCollapsedChameleon(oldNode)
                 ? ThreeState.NO  // do not dive into collapsed nodes
                 : ThreeState.UNSURE;
        }

        if (oldNode.getElementType() instanceof ILazyParseableElementType && type instanceof ILazyParseableElementType ||
            oldNode.getElementType() instanceof ICustomParsingType && type instanceof ICustomParsingType) {
          return ((TreeElement)oldNode).textMatches(token.getText())
                 ? ThreeState.YES
                 : ThreeState.NO;
        }
      }

      return ThreeState.UNSURE;
    }

    private @NotNull ThreeState customCompare(@NotNull ASTNode oldNode, @NotNull LighterASTNode newNode) {
      for (CustomLanguageASTComparator comparator : myCustomLanguageASTComparators) {
        ThreeState customComparatorResult = comparator.compareAST(oldNode, newNode, myTreeStructure);
        if (customComparatorResult != ThreeState.UNSURE) {
          return customComparatorResult;
        }
      }

      if (myCustom != null) {
        return myCustom.fun(oldNode, newNode, myTreeStructure);
      }

      return ThreeState.UNSURE;
    }

    @Override
    public boolean typesEqual(@NotNull ASTNode n1, @NotNull LighterASTNode n2) {
      if (n1 instanceof PsiWhiteSpaceImpl) {
        return ourAnyLanguageWhitespaceTokens.contains(n2.getTokenType()) ||
               n2 instanceof Token && ((Token)n2).getBuilder().myWhitespaces.contains(n2.getTokenType());
      }
      IElementType n1t;
      IElementType n2t;
      if (n1 instanceof ForeignLeafPsiElement) {
        n1t = ((ForeignLeafPsiElement)n1).getForeignType();
        n2t = n2.getTokenType();
      }
      else {
        n1t = dereferenceToken(n1.getElementType());
        n2t = dereferenceToken(n2.getTokenType());
      }

      return Comparing.equal(n1t, n2t);
    }

    private static IElementType dereferenceToken(IElementType probablyWrapper) {
      if (probablyWrapper instanceof TokenWrapper) {
        return dereferenceToken(((TokenWrapper)probablyWrapper).getDelegate());
      }
      return probablyWrapper;
    }


    @Override
    public boolean hashCodesEqual(@NotNull ASTNode n1, @NotNull LighterASTNode n2) {
      if (n1 instanceof LeafElement && n2 instanceof Token) {
        boolean isForeign1 = n1 instanceof ForeignLeafPsiElement;
        boolean isForeign2 = n2.getTokenType() instanceof ForeignLeafType;
        if (isForeign1 != isForeign2) return false;

        if (isForeign1) {
          return n1.getText().equals(((ForeignLeafType)n2.getTokenType()).getValue());
        }

        return ((LeafElement)n1).textMatches(((Token)n2).getText());
      }

      if (n1 instanceof PsiErrorElement && n2.getTokenType() == TokenType.ERROR_ELEMENT) {
        PsiErrorElement e1 = (PsiErrorElement)n1;
        if (!Objects.equals(e1.getErrorDescription(), getErrorMessage(n2))) return false;
      }

      return ((Node)n2).tokenTextMatches(n1.getChars());
    }
  }

  private static class MyTreeStructure implements FlyweightCapableTreeStructure<LighterASTNode> {
    private final LimitedPool<TokenRangeNode> myRangePool;
    private final LimitedPool<SingleLexemeNode> myLexemePool;
    private final StartMarker myRoot;

    MyTreeStructure(@NotNull StartMarker root, @Nullable MyTreeStructure parentTree) {
      if (parentTree == null) {
        myRangePool = new LimitedPool<>(1000, new LimitedPool.ObjectFactory<TokenRangeNode>() {
          @Override
          public void cleanup(@NotNull TokenRangeNode token) {
            token.clean();
          }

          @Override
          public @NotNull TokenRangeNode create() {
            return new TokenRangeNode();
          }
        });
        myLexemePool = new LimitedPool<>(1000, new LimitedPool.ObjectFactory<SingleLexemeNode>() {
          @Override
          public @NotNull SingleLexemeNode create() {
            return new SingleLexemeNode();
          }

          @Override
          public void cleanup(@NotNull SingleLexemeNode node) {
            node.clean();
          }
        });
      }
      else {
        myRangePool = parentTree.myRangePool;
        myLexemePool = parentTree.myLexemePool;
      }
      myRoot = root;
    }

    @Override
    public @NotNull LighterASTNode getRoot() {
      return myRoot;
    }

    @Override
    public LighterASTNode getParent(@NotNull LighterASTNode node) {
      if (node instanceof ProductionMarker) {
        return ((ProductionMarker)node).myParent;
      }
      if (node instanceof Token) {
        return ((Token)node).myParentNode;
      }
      throw new UnsupportedOperationException("Unknown node type: " + node);
    }

    private int count;
    private LighterASTNode[] nodes;

    @Override
    public int getChildren(@NotNull LighterASTNode item, @NotNull Ref<LighterASTNode[]> into) {
      if (item instanceof LazyParseableToken) {
        FlyweightCapableTreeStructure<LighterASTNode> tree = ((LazyParseableToken)item).parseContents();
        LighterASTNode root = tree.getRoot();
        if (root instanceof ProductionMarker) {
          ((ProductionMarker)root).myParent = ((Token)item).myParentNode;
        }
        return tree.getChildren(root, into);  // todo: set offset shift for kids?
      }

      if (item instanceof Token || item instanceof ErrorItem) return 0;
      StartMarker marker = (StartMarker)item;

      count = 0;
      ProductionMarker child = marker.myFirstChild;
      int lexIndex = marker.myLexemeIndex;
      while (child != null) {
        lexIndex = insertLeaves(lexIndex, child.myLexemeIndex, marker.myBuilder, marker);

        if (child instanceof StartMarker && child.isCollapsed()) {
          int lastIndex = child.getEndIndex();
          insertLeaf(child.getTokenType(), marker.myBuilder, child.myLexemeIndex, lastIndex, true, marker);
        }
        else {
          ensureCapacity();
          nodes[count++] = child;
        }

        if (child instanceof StartMarker) {
          lexIndex = child.getEndIndex();
        }
        child = child.myNext;
      }

      insertLeaves(lexIndex, marker.getEndIndex(), marker.myBuilder, marker);
      into.set(nodes == null ? LighterASTNode.EMPTY_ARRAY : nodes);
      nodes = null;

      return count;
    }

    @Override
    public void disposeChildren(LighterASTNode[] nodes, int count) {
      if (nodes == null) return;
      for (int i = 0; i < count; i++) {
        LighterASTNode node = nodes[i];
        if (node instanceof TokenRangeNode) {
          myRangePool.recycle((TokenRangeNode)node);
        }
        else if (node instanceof SingleLexemeNode) {
          myLexemePool.recycle((SingleLexemeNode)node);
        }
      }
    }

    private void ensureCapacity() {
      LighterASTNode[] old = nodes;
      if (old == null) {
        old = new LighterASTNode[10];
        nodes = old;
      }
      else if (count >= old.length) {
        nodes = Arrays.copyOf(old, count * 3 / 2);
      }
    }

    private int insertLeaves(int curToken, int lastIdx, @NotNull PsiBuilderImpl builder, @NotNull StartMarker parent) {
      lastIdx = Math.min(lastIdx, builder.myLexemeCount);
      while (curToken < lastIdx) {
        insertLeaf(builder.myLexTypes[curToken], builder, curToken, curToken + 1, false, parent);

        curToken++;
      }
      return curToken;
    }

    private void insertLeaf(@NotNull IElementType type,
                            @NotNull PsiBuilderImpl builder,
                            int startLexemeIndex,
                            int endLexemeIndex,
                            boolean forceInsertion,
                            @NotNull StartMarker parent) {
      int start = builder.myLexStarts[startLexemeIndex];
      int end = builder.myLexStarts[endLexemeIndex];
      /* Corresponding code for heavy tree is located in `PsiBuilderImpl#insertLeaves` and is applied only to plain lexemes */
      if (start > end || !forceInsertion && start == end && !(type instanceof ILeafElementType)) {
        return;
      }

      Token lexeme;
      if (type instanceof ILightLazyParseableElementType) {
        int startInFile = start + builder.myOffset;
        LazyParseableToken token = builder.myChameleonCache.get(startInFile);
        if (token == null) {
          token = new LazyParseableToken(this, startLexemeIndex, endLexemeIndex);
          token.initToken(type, parent, start, end);
          builder.myChameleonCache.put(startInFile, token);
        }
        else if (token.getBuilder() != builder || token.myStartIndex != startLexemeIndex || token.myEndIndex != endLexemeIndex) {
          throw new AssertionError("Wrong chameleon cached");
        }
        lexeme = token;
      }
      else if (startLexemeIndex == endLexemeIndex - 1 && type == builder.myLexTypes[startLexemeIndex]) {
        SingleLexemeNode single = myLexemePool.alloc();
        single.myParentNode = parent;
        single.myLexemeIndex = startLexemeIndex;
        lexeme = single;
      }
      else {
        TokenRangeNode collapsed = myRangePool.alloc();
        collapsed.initToken(type, parent, start, end);
        lexeme = collapsed;
      }
      ensureCapacity();
      nodes[count++] = lexeme;
    }

    @Override
    public @NotNull CharSequence toString(@NotNull LighterASTNode node) {
      return myRoot.myBuilder.myText.subSequence(node.getStartOffset(), node.getEndOffset());
    }

    @Override
    public int getStartOffset(@NotNull LighterASTNode node) {
      return node.getStartOffset();
    }

    @Override
    public int getEndOffset(@NotNull LighterASTNode node) {
      return node.getEndOffset();
    }
  }

  private static final class ASTConverter implements Function<Node, ASTNode> {
    private final StartMarker myRoot;

    private ASTConverter(@NotNull StartMarker root) {
      myRoot = root;
    }

    @Override
    public ASTNode apply(Node n) {
      if (n instanceof Token) {
        Token token = (Token)n;
        return token.getBuilder().createLeaf(token.getTokenType(), token.getStartOffsetInBuilder(), token.getEndOffsetInBuilder());
      }
      else if (n instanceof ErrorItem) {
        return Factory.createErrorElement(((ErrorItem)n).myMessage);
      }
      else {
        StartMarker startMarker = (StartMarker)n;
        CompositeElement composite = n == myRoot ? (CompositeElement)myRoot.myBuilder.createRootAST(myRoot)
                                                 : createComposite(startMarker, startMarker.myBuilder.getASTFactory());
        startMarker.myBuilder.bind(startMarker, composite);
        return composite;
      }
    }
  }

  @Override
  public void setDebugMode(boolean dbgMode) {
    myDebugMode = dbgMode;
  }

  public int getLexemeCount() {
    return myLexemeCount;
  }

  public @NotNull Lexer getLexer() {
    return myLexer;
  }

  protected @NotNull TreeElement createLeaf(@NotNull IElementType type, int start, int end) {
    CharSequence text = getInternedText(start, end);
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ICustomParsingType) {
      return (TreeElement)((ICustomParsingType)type).parse(text, myCharTable);
    }

    ASTFactory astFactory = getASTFactory();
    if (type instanceof ILazyParseableElementType) {
      return createLazy((ILazyParseableElementType)type, text, astFactory);
    }

    if (astFactory != null) {
      TreeElement element = astFactory.createLeaf(type, text);
      if (element != null) return element;
    }

    return ASTFactory.leaf(type, text);
  }

  protected @NotNull CharSequence getInternedText(int start, int end) {
    return myCharTable.intern(myText, start, end);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    //noinspection unchecked
    return key == FileContextUtil.CONTAINING_FILE_KEY ? (T)myFile : super.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (key == FileContextUtil.CONTAINING_FILE_KEY) {
      myFile = (PsiFile)value;
    }
    else {
      super.putUserData(key, value);
    }
  }

  /**
   * @return lexing time in nanoseconds
   * @see #performLexing(Object)
   */
  public long getLexingTimeNs() {
    return myLexingTimeNs;
  }
}

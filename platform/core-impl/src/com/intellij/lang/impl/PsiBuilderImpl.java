/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
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
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import com.intellij.util.ThreeState;
import com.intellij.util.TripleFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.lang.WhitespacesBinders.DEFAULT_RIGHT_BINDER;

/**
 * @author max
 */
public class PsiBuilderImpl extends UserDataHolderBase implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  // function stored in PsiBuilderImpl' user data which called during reparse when merge algorithm is not sure what to merge
  public static final Key<TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>>
    CUSTOM_COMPARATOR = Key.create("CUSTOM_COMPARATOR");

  private static final Key<TokenSequence> LAZY_PARSEABLE_TOKENS = Key.create("LAZY_PARSEABLE_TOKENS");

  private static TokenSet ourAnyLanguageWhitespaceTokens = TokenSet.EMPTY;

  private final Project myProject;
  private PsiFile myFile;

  private final int[] myLexStarts;
  private final IElementType[] myLexTypes;
  private int myCurrentLexeme;

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

  private Map<Key, Object> myUserData;
  private IElementType myCachedTokenType;

  private final TIntObjectHashMap<LazyParseableToken> myChameleonCache = new TIntObjectHashMap<>();
  private final MarkerPool myPool = new MarkerPool(this);
  private final MarkerOptionalData myOptionalData = new MarkerOptionalData();
  private final MarkerProduction myProduction = new MarkerProduction(myPool, myOptionalData);

  public static void registerWhitespaceToken(@NotNull IElementType type) {
    ourAnyLanguageWhitespaceTokens = TokenSet.orSet(ourAnyLanguageWhitespaceTokens, TokenSet.create(type));
  }

  public PsiBuilderImpl(@Nullable Project project,
                        @Nullable PsiFile containingFile,
                        @NotNull ParserDefinition parserDefinition,
                        @NotNull Lexer lexer,
                        @Nullable CharTable charTable,
                        @NotNull final CharSequence text,
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
  }

  private TokenSequence performLexing(@Nullable Object parentCachingNode) {
    TokenSequence fromParent = null;

    if (parentCachingNode instanceof LazyParseableToken) {
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

    return new TokenSequence.Builder(myText, myLexer).performLexing();
  }

  private static boolean doLexingOptimizationCorrectionCheck() {
    return false; // set to true to check that re-lexing of lazy parseables produces the same sequence as cached one
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
  @Nullable
  public StartMarker getLatestDoneMarker() {
    int index = myProduction.size() - 1;
    while (index >= 0) {
      PsiBuilderImpl.StartMarker marker = myProduction.getDoneMarkerAt(index);
      if (marker != null) return marker;
      --index;
    }
    return null;
  }

  private interface Node extends LighterASTNode {
    int hc();
  }

  public abstract static class ProductionMarker implements Node {
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

    public void remapTokenType(@NotNull IElementType type) {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public int getStartIndex() {
      return myLexemeIndex;
    }

    public int getEndIndex() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    @NotNull
    abstract WhitespacesAndCommentsBinder getBinder(boolean done);

    abstract void setLexemeIndex(int lexemeIndex, boolean done);
    
    abstract int getLexemeIndex(boolean done);
  }

  static class StartMarker extends ProductionMarker implements Marker {
    private IElementType myType;
    private int myDoneLexeme = -1;
    private ProductionMarker myFirstChild;
    private ProductionMarker myLastChild;
    private int myHC = -1;

    StartMarker(int markerId, PsiBuilderImpl builder) {
      super(markerId, builder);
    }

    @Override
    void clean() {
      super.clean();
      myBuilder.myOptionalData.clean(markerId);

      myType = null;
      myDoneLexeme = -1;
      myFirstChild = myLastChild = null;
      myHC = -1;
    }

    @Override
    public int hc() {
      if (myHC == -1) {
        PsiBuilderImpl builder = myBuilder;
        int hc = 0;
        final CharSequence buf = builder.myText;
        final char[] bufArray = builder.myTextArray;
        ProductionMarker child = myFirstChild;
        int lexIdx = myLexemeIndex;

        while (child != null) {
          int lastLeaf = child.myLexemeIndex;
          for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[lastLeaf]; i++) {
            hc += bufArray != null ? bufArray[i] : buf.charAt(i);
          }
          lexIdx = lastLeaf;
          hc += child.hc();
          if (child instanceof StartMarker) {
            lexIdx = child.getEndIndex();
          }
          child = child.myNext;
        }

        for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[getEndIndex()]; i++) {
          hc += bufArray != null ? bufArray[i] : buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    @Override
    public int getEndOffset() {
      return myBuilder.myLexStarts[getEndIndex()] + myBuilder.myOffset;
    }

    @Override
    public int getEndIndex() {
      return myDoneLexeme;
    }

    @NotNull
    @Override
    WhitespacesAndCommentsBinder getBinder(boolean done) {
      return myBuilder.myOptionalData.getBinder(markerId, done);
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
        myLastChild = node;
      }
      else {
        myLastChild.myNext = node;
        myLastChild = node;
      }
    }

    @NotNull
    @Override
    public Marker precede() {
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
      myType = type;
      myBuilder.processDone(this, null, (StartMarker)before);
    }

    @Override
    public void doneBefore(@NotNull final IElementType type, @NotNull final Marker before, final String errorMessage) {
      StartMarker marker = (StartMarker)before;
      ErrorItem errorItem = myBuilder.myPool.allocateErrorItem();
      errorItem.myMessage = errorMessage;
      errorItem.myLexemeIndex = marker.myLexemeIndex;
      myBuilder.myProduction.addBefore(errorItem, marker);
      doneBefore(type, before);
    }

    @Override
    public void error(String message) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.processDone(this, message, null);
    }

    @Override
    public void errorBefore(final String message, @NotNull final Marker before) {
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
    public void setCustomEdgeTokenBinders(final WhitespacesAndCommentsBinder left, final WhitespacesAndCommentsBinder right) {
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
  }

  @NotNull
  private Marker precede(final StartMarker marker) {
    assert marker.myLexemeIndex >= 0 : "Preceding disposed marker";
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker);
    }
    StartMarker pre = createMarker(marker.myLexemeIndex);
    myProduction.addBefore(pre, marker);
    return pre;
  }

  private abstract static class Token implements Node {
    protected PsiBuilderImpl myBuilder;
    private IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myHC = -1;
    private StartMarker myParentNode;

    public void clean() {
      myBuilder = null;
      myHC = -1;
      myParentNode = null;
    }

    @Override
    public int hc() {
      if (myHC == -1) {
        int hc = 0;
        if (myTokenType instanceof TokenWrapper) {
          final String value = ((TokenWrapper)myTokenType).getValue();
          for (int i = 0; i < value.length(); i++) {
            hc += value.charAt(i);
          }
        }
        else {
          final int start = myTokenStart;
          final int end = myTokenEnd;
          final CharSequence buf = myBuilder.myText;
          final char[] bufArray = myBuilder.myTextArray;

          for (int i = start; i < end; i++) {
            hc += bufArray != null ? bufArray[i] : buf.charAt(i);
          }
        }

        myHC = hc;
      }

      return myHC;
    }

    @Override
    public int getEndOffset() {
      return myTokenEnd + myBuilder.myOffset;
    }

    @Override
    public int getStartOffset() {
      return myTokenStart + myBuilder.myOffset;
    }

    @NotNull
    public CharSequence getText() {
      if (myTokenType instanceof TokenWrapper) {
        return ((TokenWrapper)myTokenType).getValue();
      }

      return myBuilder.myText.subSequence(myTokenStart, myTokenEnd);
    }

    @NotNull
    @Override
    public IElementType getTokenType() {
      return myTokenType;
    }

    void initToken(@NotNull IElementType type,
                   @NotNull PsiBuilderImpl builder,
                   StartMarker parent,
                   int start,
                   int end) {
      myParentNode = parent;
      myBuilder = builder;
      myTokenType = type;
      myTokenStart = start;
      myTokenEnd = end;
    }
  }

  private static class TokenNode extends Token implements LighterASTTokenNode {
    @Override
    public String toString() {
      return getText().toString();
    }
  }

  private static class LazyParseableToken extends Token implements LighterLazyParseableNode {
    private MyTreeStructure myParentStructure;
    private FlyweightCapableTreeStructure<LighterASTNode> myParsed;
    private int myStartIndex;
    private int myEndIndex;

    @Override
    public void clean() {
      myBuilder.myChameleonCache.remove(getStartOffset());
      super.clean();
      myParentStructure = null;
      myParsed = null;
    }

    @Override
    public PsiFile getContainingFile() {
      return myBuilder.myFile;
    }

    @Override
    public CharTable getCharTable() {
      return myBuilder.myCharTable;
    }

    public FlyweightCapableTreeStructure<LighterASTNode> parseContents() {
      if (myParsed == null) {
        myParsed = ((ILightLazyParseableElementType)getTokenType()).parseContents(this);
      }
      return myParsed;
    }

    @Override
    public boolean accept(@NotNull Visitor visitor) {
      for (int i = myStartIndex; i < myEndIndex; i++) {
        IElementType type = myBuilder.myLexTypes[i];
        if (!visitor.visit(type)) {
          return false;
        }
      }

      return true;
    }

    @Nullable
    private TokenSequence getParsedTokenSequence() {
      int tokenCount = myEndIndex - myStartIndex;
      if (tokenCount == 1) return null; // not expand single lazy parseable token case
      
      int[] lexStarts = new int[tokenCount + 1];
      System.arraycopy(myBuilder.myLexStarts, myStartIndex, lexStarts, 0, tokenCount);
      int diff = myBuilder.myLexStarts[myStartIndex];
      for(int i = 0; i < tokenCount; ++i) lexStarts[i] -= diff;
      lexStarts[tokenCount] = getEndOffset() - getStartOffset();
      
      IElementType[] lexTypes = new IElementType[tokenCount + 1];
      System.arraycopy(myBuilder.myLexTypes, myStartIndex, lexTypes, 0, tokenCount);
      
      return new TokenSequence(lexStarts, lexTypes, tokenCount);
    }
  }

  static class ErrorItem extends ProductionMarker {
    private String myMessage;

    ErrorItem(int markerId, PsiBuilderImpl builder) {
      super(markerId, builder);
    }

    @Override
    void clean() {
      super.clean();
      myMessage = null;
    }

    @NotNull
    @Override
    public WhitespacesAndCommentsBinder getBinder(boolean done) {
      assert !done;
      return DEFAULT_RIGHT_BINDER;
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
    public int hc() {
      return 0;
    }

    @Override
    public int getEndOffset() {
      return myBuilder.myLexStarts[myLexemeIndex] + myBuilder.myOffset;
    }

    @NotNull
    @Override
    public IElementType getTokenType() {
      return TokenType.ERROR_ELEMENT;
    }
  }

  @Override
  public CharSequence getOriginalText() {
    return myText;
  }

  @Override
  @Nullable
  public IElementType getTokenType() {
    IElementType cached = myCachedTokenType;
    if (cached == null) {
      myCachedTokenType = cached = calcTokenType();
    }
    return cached;
  }

  private void clearCachedTokenType() {
    myCachedTokenType = null;
  }

  private IElementType remapCurrentToken() {
    if (myCachedTokenType != null) return myCachedTokenType;
    if (myRemapper != null) {
      remapCurrentToken(myRemapper.filter(myLexTypes[myCurrentLexeme], myLexStarts[myCurrentLexeme],
                                          myLexStarts[myCurrentLexeme + 1], myLexer.getBufferSequence()));
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
  public void remapCurrentToken(IElementType type) {
    myLexTypes[myCurrentLexeme] = type;
    clearCachedTokenType();
  }

  @Nullable
  @Override
  public IElementType lookAhead(int steps) {
    if (eof()) {    // ensure we skip over whitespace if it's needed
      return null;
    }
    int cur = myCurrentLexeme;

    while (steps > 0) {
      ++cur;
      while (cur < myLexemeCount && whitespaceOrComment(myLexTypes[cur])) {
        cur++;
      }

      steps--;
    }

    return cur < myLexemeCount ? myLexTypes[cur] : null;
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
  public void setWhitespaceSkippedCallback(@Nullable final WhitespaceSkippedCallback callback) {
    myWhitespaceSkippedCallback = callback;
  }

  @Override
  public void advanceLexer() {
    ProgressIndicatorProvider.checkCanceled();

    if (eof()) return;

    if (!myTokenTypeChecked) {
      LOG.error("Probably a bug: eating token without its type checking");
    }

    myTokenTypeChecked = false;
    myCurrentLexeme++;
    clearCachedTokenType();
  }

  private void skipWhitespace() {
    while (myCurrentLexeme < myLexemeCount && whitespaceOrComment(remapCurrentToken())) {
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
  @Nullable
  public String getTokenText() {
    if (eof()) return null;
    final IElementType type = getTokenType();
    if (type instanceof TokenWrapper) {
      return ((TokenWrapper)type).getValue();
    }
    return myText.subSequence(myLexStarts[myCurrentLexeme], myLexStarts[myCurrentLexeme + 1]).toString();
  }

  public boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.contains(token) || myComments.contains(token);
  }

  @NotNull
  @Override
  public Marker mark() {
    if (!myProduction.isEmpty()) {
      skipWhitespace();
    }

    StartMarker marker = createMarker(myCurrentLexeme);
    myProduction.addMarker(marker);
    return marker;
  }

  @NotNull
  private StartMarker createMarker(final int lexemeIndex) {
    StartMarker marker = myPool.allocateStartMarker();
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
    assert marker.myLexemeIndex >= 0 : "Disposed marker passed to rollbackTo";
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker);
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

  private void processDone(@NotNull StartMarker marker, @Nullable String errorMessage, @Nullable StartMarker before) {
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

  private boolean isEmpty(final int startIdx, final int endIdx) {
    for (int i = startIdx; i < endIdx; i++) {
      final IElementType token = myLexTypes[i];
      if (!whitespaceOrComment(token)) return false;
    }
    return true;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void doValidityChecks(@NotNull StartMarker marker, @Nullable StartMarker before) {
    if (marker.isDone()) {
      LOG.error("Marker already done.");
    }

    if (myDebugMode) {
      myProduction.doHeavyChecksOnMarkerDone(marker, before);
    }
  }

  @Override
  public void error(String messageText) {
    ProductionMarker lastMarker = myProduction.getStartingMarkerAt(myProduction.size() - 1);
    if (lastMarker instanceof ErrorItem && lastMarker.myLexemeIndex == myCurrentLexeme) {
      return;
    }
    ErrorItem marker = myPool.allocateErrorItem();
    marker.myMessage = messageText;
    marker.myLexemeIndex = myCurrentLexeme;
    myProduction.addMarker(marker);
  }

  @Override
  @NotNull
  public ASTNode getTreeBuilt() {
    return buildTree();
  }

  @NotNull
  private ASTNode buildTree() {
    final StartMarker rootMarker = prepareLightTree();
    final boolean isTooDeep = myFile != null && BlockSupport.isTooDeep(myFile.getOriginalFile());

    if (myOriginalTree != null && !isTooDeep) {
      DiffLog diffLog = merge(myOriginalTree, rootMarker, myLastCommittedText);
      throw new BlockSupport.ReparsedSuccessfullyException(diffLog);
    }

    final TreeElement rootNode = createRootAST(rootMarker);
    bind(rootMarker, (CompositeElement)rootNode);

    if (isTooDeep && !(rootNode instanceof FileElement)) {
      final ASTNode childNode = rootNode.getFirstChildNode();
      childNode.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
    }

    assert rootNode.getTextLength() == myText.length() : rootNode.getElementType();

    return rootNode;
  }

  @Override
  @NotNull
  public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    final StartMarker rootMarker = prepareLightTree();
    return new MyTreeStructure(rootMarker, myParentLightTree);
  }

  @NotNull
  private TreeElement createRootAST(@NotNull StartMarker rootMarker) {
    final IElementType type = rootMarker.getTokenType();
    @SuppressWarnings("NullableProblems")
    final TreeElement rootNode = type instanceof ILazyParseableElementType ?
                             ASTFactory.lazy((ILazyParseableElementType)type, null) : createComposite(rootMarker);
    if (myCharTable == null) {
      myCharTable = rootNode instanceof FileElement ? ((FileElement)rootNode).getCharTable() : new CharTableImpl();
    }
    if (!(rootNode instanceof FileElement)) {
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);
    }
    return rootNode;
  }

  private static class ConvertFromTokensToASTBuilder implements DiffTreeChangeBuilder<ASTNode, LighterASTNode> {
    private final DiffTreeChangeBuilder<ASTNode, ASTNode> myDelegate;
    private final ASTConverter myConverter;

    private ConvertFromTokensToASTBuilder(@NotNull StartMarker rootNode, @NotNull DiffTreeChangeBuilder<ASTNode, ASTNode> delegate) {
      myDelegate = delegate;
      myConverter = new ASTConverter(rootNode);
    }

    @Override
    public void nodeDeleted(@NotNull final ASTNode oldParent, @NotNull final ASTNode oldNode) {
      myDelegate.nodeDeleted(oldParent, oldNode);
    }

    @Override
    public void nodeInserted(@NotNull final ASTNode oldParent, @NotNull final LighterASTNode newNode, final int pos) {
      myDelegate.nodeInserted(oldParent, myConverter.convert((Node)newNode), pos);
    }

    @Override
    public void nodeReplaced(@NotNull final ASTNode oldChild, @NotNull final LighterASTNode newChild) {
      ASTNode converted = myConverter.convert((Node)newChild);
      myDelegate.nodeReplaced(oldChild, converted);
    }
  }

  @NonNls private static final String UNBALANCED_MESSAGE =
    "Unbalanced tree. Most probably caused by unbalanced markers. " +
    "Try calling setDebugMode(true) against PsiBuilder passed to identify exact location of the problem";

  @NotNull
  private DiffLog merge(@NotNull final ASTNode oldRoot, @NotNull StartMarker newRoot, @NotNull CharSequence lastCommittedText) {
    DiffLog diffLog = new DiffLog();
    DiffTreeChangeBuilder<ASTNode, LighterASTNode> builder = new ConvertFromTokensToASTBuilder(newRoot, diffLog);
    MyTreeStructure treeStructure = new MyTreeStructure(newRoot, null);
    final List<CustomLanguageASTComparator> customLanguageASTComparators = CustomLanguageASTComparator.getMatchingComparators(myFile);
    ShallowNodeComparator<ASTNode, LighterASTNode> comparator =
      new MyComparator(getUserDataUnprotected(CUSTOM_COMPARATOR), customLanguageASTComparators, treeStructure);

    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    BlockSupportImpl.diffTrees(oldRoot, builder, comparator, treeStructure, indicator == null ? new EmptyProgressIndicator() : indicator,
                               lastCommittedText);
    return diffLog;
  }

  @NotNull
  private StartMarker prepareLightTree() {
    if (myProduction.isEmpty()) {
      LOG.error("Parser produced no markers. Text:\n" + myText);
    }
    // build tree only once to avoid threading issues in read-only PSI
    StartMarker rootMarker = (StartMarker)Objects.requireNonNull(myProduction.getStartingMarkerAt(0));
    if (rootMarker.myFirstChild != null) return rootMarker;

    myOptionalData.compact();

    myTokenTypeChecked = true;
    balanceWhiteSpaces();

    rootMarker.myParent = rootMarker.myFirstChild = rootMarker.myLastChild = rootMarker.myNext = null;
    StartMarker curNode = rootMarker;
    final Stack<StartMarker> nodes = ContainerUtil.newStack();
    nodes.push(rootMarker);

    int lastErrorIndex = -1;
    int maxDepth = 0;
    int curDepth = 0;
    for (int i = 1; i < myProduction.size(); i++) {
      ProductionMarker item = myProduction.getStartingMarkerAt(i);

      if (item instanceof StartMarker) {
        final StartMarker marker = (StartMarker)item;
        marker.myParent = curNode;
        marker.myFirstChild = marker.myLastChild = marker.myNext = null;
        curNode.addChild(marker);
        nodes.push(curNode);
        curNode = marker;
        curDepth++;
        if (curDepth > maxDepth) maxDepth = curDepth;
      }
      else if (item instanceof ErrorItem) {
        ((ErrorItem)item).myParent = curNode;
        int curToken = item.myLexemeIndex;
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
      else {
        assertMarkersBalanced(myProduction.getDoneMarkerAt(i) == curNode, item);
        curNode = nodes.pop();
        curDepth--;
      }
    }

    if (myCurrentLexeme < myLexemeCount) {
      final List<IElementType> missed = ContainerUtil.newArrayList(myLexTypes, myCurrentLexeme, myLexemeCount);
      LOG.error("Tokens " + missed + " were not inserted into the tree. " +(myFile != null? myFile.getLanguage()+", ":"")+"Text:\n" + myText);
    }

    if (rootMarker.getEndIndex() < myLexemeCount) {
      final List<IElementType> missed = ContainerUtil.newArrayList(myLexTypes, rootMarker.getEndIndex(), myLexemeCount);
      LOG.error("Tokens " + missed + " are outside of root element \"" + rootMarker.myType + "\". Text:\n" + myText);
    }

    assertMarkersBalanced(curNode == rootMarker, curNode);

    checkTreeDepth(maxDepth, rootMarker.getTokenType() instanceof IFileElementType);

    clearCachedTokenType();
    return rootMarker;
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
    LOG.error(UNBALANCED_MESSAGE + "\n" +
              "language: " + language + "\n" +
              "context: '" + context + "'");
  }

  private void balanceWhiteSpaces() {
    RelativeTokenTypesView wsTokens = new RelativeTokenTypesView();
    RelativeTokenTextView tokenTextGetter = new RelativeTokenTextView();
    int lastIndex = 0;

    for (int i = 1, size = myProduction.size() - 1; i < size; i++) {
      ProductionMarker starting = myProduction.getStartingMarkerAt(i);
      if (starting instanceof StartMarker) {
        assertMarkersBalanced(((StartMarker)starting).isDone(), starting);
      }
      boolean done = starting == null;
      ProductionMarker item = starting != null ? starting : Objects.requireNonNull(myProduction.getDoneMarkerAt(i));

      WhitespacesAndCommentsBinder binder = item.getBinder(done);
      int lexemeIndex = item.getLexemeIndex(done);

      boolean recursive = binder instanceof WhitespacesAndCommentsBinder.RecursiveBinder;
      int prevProductionLexIndex = recursive ? 0 : myProduction.getLexemeIndexAt(i - 1);
      int wsStartIndex = Math.max(lexemeIndex, lastIndex);
      while (wsStartIndex > prevProductionLexIndex && whitespaceOrComment(myLexTypes[wsStartIndex - 1])) wsStartIndex--;

      int wsEndIndex = lexemeIndex;
      while (wsEndIndex < myLexemeCount && whitespaceOrComment(myLexTypes[wsEndIndex])) wsEndIndex++;

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
    @NotNull
    public CharSequence get(int i) {
      return myText.subSequence(myLexStarts[myStart + i], myLexStarts[myStart + i + 1]);
    }
  }

  private void checkTreeDepth(final int maxDepth, final boolean isFileRoot) {
    if (myFile == null) return;
    final PsiFile file = myFile.getOriginalFile();
    final Boolean flag = file.getUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED);
    if (maxDepth > BlockSupport.INCREMENTAL_REPARSE_DEPTH_LIMIT) {
      if (!Boolean.TRUE.equals(flag)) {
        file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
      }
    }
    else if (isFileRoot && flag != null) {
      file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, null);
    }
  }

  private void bind(@NotNull StartMarker rootMarker, @NotNull CompositeElement rootNode) {
    StartMarker curMarker = rootMarker;
    CompositeElement curNode = rootNode;

    int lexIndex = rootMarker.myLexemeIndex;
    ProductionMarker item = rootMarker.myFirstChild != null ? rootMarker.myFirstChild : rootMarker;
    boolean itemDone = rootMarker.myFirstChild == null;
    while (true) {
      lexIndex = insertLeaves(lexIndex, item.getLexemeIndex(itemDone), curNode);

      if (item == rootMarker && itemDone) break;

      if (item instanceof StartMarker) {
        final StartMarker marker = (StartMarker)item;
        if (itemDone) {
          curMarker = (StartMarker)marker.myParent;
          curNode = curNode.getTreeParent();
          item = marker.myNext;
          itemDone = false;
        }
        else if (!myOptionalData.isCollapsed(marker.markerId)) {
          curMarker = marker;

          final CompositeElement childNode = createComposite(marker);
          curNode.rawAddChildrenWithoutNotifications(childNode);
          curNode = childNode;

          item = marker.myFirstChild != null ? marker.myFirstChild : marker;
          itemDone = marker.myFirstChild == null;
          continue;
        }
        else {
          lexIndex = collapseLeaves(curNode, marker);
          item = marker.myNext;
          itemDone = false;
        }
      }
      else if (item instanceof ErrorItem) {
        final CompositeElement errorElement = Factory.createErrorElement(((ErrorItem)item).myMessage);
        curNode.rawAddChildrenWithoutNotifications(errorElement);
        item = ((ErrorItem)item).myNext;
        itemDone = false;
      }

      if (item == null) {
        item = curMarker;
        itemDone = true;
      }
    }
  }

  private int insertLeaves(int curToken, int lastIdx, final CompositeElement curNode) {
    lastIdx = Math.min(lastIdx, myLexemeCount);
    while (curToken < lastIdx) {
      ProgressIndicatorProvider.checkCanceled();
      final int start = myLexStarts[curToken];
      final int end = myLexStarts[curToken + 1];
      if (start < end || myLexTypes[curToken] instanceof ILeafElementType) { // Empty token. Most probably a parser directive like indent/dedent in Python
        final IElementType type = myLexTypes[curToken];
        final TreeElement leaf = createLeaf(type, start, end);
        curNode.rawAddChildrenWithoutNotifications(leaf);
      }
      curToken++;
    }

    return curToken;
  }

  private int collapseLeaves(@NotNull CompositeElement ast, @NotNull StartMarker startMarker) {
    final int start = myLexStarts[startMarker.myLexemeIndex];
    final int end = myLexStarts[startMarker.getEndIndex()];
    final IElementType markerType = startMarker.myType;
    final TreeElement leaf = createLeaf(markerType, start, end);
    if (markerType instanceof ILazyParseableElementType && ((ILazyParseableElementType)markerType).reuseCollapsedTokens() &&
        startMarker.myLexemeIndex < startMarker.getEndIndex()) {
      int length = startMarker.getEndIndex() - startMarker.myLexemeIndex;
      int[] relativeStarts = new int[length + 1];
      IElementType[] types = new IElementType[length + 1];
      for (int i = startMarker.myLexemeIndex; i < startMarker.getEndIndex(); i++) {
        relativeStarts[i - startMarker.myLexemeIndex] = myLexStarts[i] - start;
        types[i - startMarker.myLexemeIndex] = myLexTypes[i];
      }
      relativeStarts[length] = end - start;
      leaf.putUserData(LAZY_PARSEABLE_TOKENS, new TokenSequence(relativeStarts, types, length));
    }
    ast.rawAddChildrenWithoutNotifications(leaf);
    return startMarker.getEndIndex();
  }

  @NotNull
  private static CompositeElement createComposite(@NotNull StartMarker marker) {
    final IElementType type = marker.myType;
    if (type == TokenType.ERROR_ELEMENT) {
      return Factory.createErrorElement(marker.myBuilder.myOptionalData.getDoneError(marker.markerId));
    }

    if (type == null) {
      throw new RuntimeException(UNBALANCED_MESSAGE);
    }

    return ASTFactory.composite(type);
  }

  @Nullable
  public static String getErrorMessage(@NotNull LighterASTNode node) {
    if (node instanceof ErrorItem) return ((ErrorItem)node).myMessage;
    if (node instanceof StartMarker) {
      final StartMarker marker = (StartMarker)node;
      if (marker.myType == TokenType.ERROR_ELEMENT) {
        return marker.myBuilder.myOptionalData.getDoneError(marker.markerId);
      }
    }

    return null;
  }

  private static class MyComparator implements ShallowNodeComparator<ASTNode, LighterASTNode> {
    private final TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> custom;
    @NotNull private final List<CustomLanguageASTComparator> myCustomLanguageASTComparators;
    private final MyTreeStructure myTreeStructure;

    private MyComparator(TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> custom,
                         @NotNull List<CustomLanguageASTComparator> customLanguageASTComparators,
                         @NotNull MyTreeStructure treeStructure) {
      this.custom = custom;
      myCustomLanguageASTComparators = customLanguageASTComparators;
      myTreeStructure = treeStructure;
    }

    @NotNull
    @Override
    public ThreeState deepEqual(@NotNull final ASTNode oldNode, @NotNull final LighterASTNode newNode) {
      ProgressIndicatorProvider.checkCanceled();

      boolean oldIsErrorElement = oldNode instanceof PsiErrorElement;
      boolean newIsErrorElement = newNode.getTokenType() == TokenType.ERROR_ELEMENT;
      if (oldIsErrorElement != newIsErrorElement) return ThreeState.NO;
      if (oldIsErrorElement) {
        final PsiErrorElement e1 = (PsiErrorElement)oldNode;
        return Comparing.equal(e1.getErrorDescription(), getErrorMessage(newNode)) ? ThreeState.UNSURE : ThreeState.NO;
      }

      final ThreeState customResult = customCompare(oldNode, newNode);
      if (customResult != ThreeState.UNSURE) {
        return customResult;
      }
      if (newNode instanceof Token) {
        final IElementType type = newNode.getTokenType();
        final Token token = (Token)newNode;

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

    @NotNull
    private ThreeState customCompare(@NotNull final ASTNode oldNode, @NotNull final LighterASTNode newNode) {
      for (CustomLanguageASTComparator comparator : myCustomLanguageASTComparators) {
        final ThreeState customComparatorResult = comparator.compareAST(oldNode, newNode, myTreeStructure);
        if (customComparatorResult != ThreeState.UNSURE) {
          return customComparatorResult;
        }
      }

      if (custom != null) {
        ThreeState customResult = custom.fun(oldNode, newNode, myTreeStructure);

        if (customResult != ThreeState.UNSURE) {
          return customResult;
        }
      }
      return ThreeState.UNSURE;
    }

    @Override
    public boolean typesEqual(@NotNull final ASTNode n1, @NotNull final LighterASTNode n2) {
      if (n1 instanceof PsiWhiteSpaceImpl) {
        return ourAnyLanguageWhitespaceTokens.contains(n2.getTokenType()) ||
               n2 instanceof Token && ((Token)n2).myBuilder.myWhitespaces.contains(n2.getTokenType());
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
    public boolean hashCodesEqual(@NotNull final ASTNode n1, @NotNull final LighterASTNode n2) {
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
        final PsiErrorElement e1 = (PsiErrorElement)n1;
        if (!Comparing.equal(e1.getErrorDescription(), getErrorMessage(n2))) return false;
      }

      return ((TreeElement)n1).hc() == ((Node)n2).hc();
    }
  }

  private static class MyTreeStructure implements FlyweightCapableTreeStructure<LighterASTNode> {
    private final LimitedPool<Token> myPool;
    private final LimitedPool<LazyParseableToken> myLazyPool;
    private final StartMarker myRoot;

    public MyTreeStructure(@NotNull StartMarker root, @Nullable final MyTreeStructure parentTree) {
      if (parentTree == null) {
        myPool = new LimitedPool<>(1000, new LimitedPool.ObjectFactory<Token>() {
          @Override
          public void cleanup(@NotNull final Token token) {
            token.clean();
          }

          @NotNull
          @Override
          public Token create() {
            return new TokenNode();
          }
        });
        myLazyPool = new LimitedPool<>(200, new LimitedPool.ObjectFactory<LazyParseableToken>() {
          @Override
          public void cleanup(@NotNull final LazyParseableToken token) {
            token.clean();
          }

          @NotNull
          @Override
          public LazyParseableToken create() {
            return new LazyParseableToken();
          }
        });
      }
      else {
        myPool = parentTree.myPool;
        myLazyPool = parentTree.myLazyPool;
      }
      myRoot = root;
    }

    @Override
    @NotNull
    public LighterASTNode getRoot() {
      return myRoot;
    }

    @Override
    public LighterASTNode getParent(@NotNull final LighterASTNode node) {
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
    public int getChildren(@NotNull final LighterASTNode item, @NotNull final Ref<LighterASTNode[]> into) {
      if (item instanceof LazyParseableToken) {
        final FlyweightCapableTreeStructure<LighterASTNode> tree = ((LazyParseableToken)item).parseContents();
        final LighterASTNode root = tree.getRoot();
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

        if (child instanceof StartMarker && child.myBuilder.myOptionalData.isCollapsed(child.markerId)) {
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
    public void disposeChildren(final LighterASTNode[] nodes, final int count) {
      if (nodes == null) return;
      for (int i = 0; i < count; i++) {
        final LighterASTNode node = nodes[i];
        if (node instanceof LazyParseableToken) {
          myLazyPool.recycle((LazyParseableToken)node);
        }
        else if (node instanceof Token) {
          myPool.recycle((Token)node);
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
        LighterASTNode[] newStore = new LighterASTNode[count * 3 / 2];
        System.arraycopy(old, 0, newStore, 0, count);
        nodes = newStore;
      }
    }

    private int insertLeaves(int curToken, int lastIdx, PsiBuilderImpl builder, StartMarker parent) {
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
                            StartMarker parent) {
      final int start = builder.myLexStarts[startLexemeIndex];
      final int end = builder.myLexStarts[endLexemeIndex];
      /* Corresponding code for heavy tree is located in {@link com.intellij.lang.impl.PsiBuilderImpl#insertLeaves}
         and is applied only to plain lexemes */
      if (start > end || !forceInsertion && start == end && !(type instanceof ILeafElementType)) {
        return;
      }

      Token lexeme = obtainToken(type, builder, startLexemeIndex, endLexemeIndex, parent, start, end);
      ensureCapacity();
      nodes[count++] = lexeme;
    }

    @NotNull
    private Token obtainToken(@NotNull IElementType type,
                              @NotNull PsiBuilderImpl builder,
                              int startLexemeIndex,
                              int endLexemeIndex, StartMarker parent, int start, int end) {
      if (type instanceof ILightLazyParseableElementType) {
        return obtainLazyToken(type, builder, startLexemeIndex, endLexemeIndex, parent, start, end);
      }

      Token lexeme = myPool.alloc();
      lexeme.initToken(type, builder, parent, start, end);
      return lexeme;
    }

    @NotNull
    private Token obtainLazyToken(@NotNull IElementType type,
                                  @NotNull PsiBuilderImpl builder,
                                  int startLexemeIndex,
                                  int endLexemeIndex, StartMarker parent, int start, int end) {
      int startInFile = start + builder.myOffset;
      LazyParseableToken token = builder.myChameleonCache.get(startInFile);
      if (token == null) {
        token = myLazyPool.alloc();
        token.myStartIndex = startLexemeIndex;
        token.myEndIndex = endLexemeIndex;
        token.initToken(type, builder, parent, start, end);
        builder.myChameleonCache.put(startInFile, token);
      } else {
        if (token.myBuilder != builder || token.myStartIndex != startLexemeIndex || token.myEndIndex != endLexemeIndex) {
          throw new AssertionError("Wrong chameleon cached");
        }
      }
      token.myParentStructure = this;
      return token;
    }

    @NotNull
    @Override
    public CharSequence toString(@NotNull LighterASTNode node) {
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

  private static class ASTConverter implements Convertor<Node, ASTNode> {
    @NotNull private final StartMarker myRoot;

    private ASTConverter(@NotNull StartMarker root) {
      myRoot = root;
    }

    @Override
    public ASTNode convert(final Node n) {
      if (n instanceof Token) {
        final Token token = (Token)n;
        return token.myBuilder.createLeaf(token.getTokenType(), token.myTokenStart, token.myTokenEnd);
      }
      else if (n instanceof ErrorItem) {
        return Factory.createErrorElement(((ErrorItem)n).myMessage);
      }
      else {
        final StartMarker startMarker = (StartMarker)n;
        final CompositeElement composite = n == myRoot ? (CompositeElement)myRoot.myBuilder.createRootAST(myRoot)
                                                         : createComposite(startMarker);
        startMarker.myBuilder.bind(startMarker, composite);
        return composite;
      }
    }
  }

  @Override
  public void setDebugMode(boolean dbgMode) {
    myDebugMode = dbgMode;
  }

  @NotNull
  public Lexer getLexer() {
    return myLexer;
  }

  @NotNull
  protected TreeElement createLeaf(@NotNull IElementType type, final int start, final int end) {
    CharSequence text = myCharTable.intern(myText, start, end);
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ICustomParsingType) {
      return (TreeElement)((ICustomParsingType)type).parse(text, myCharTable);
    }

    if (type instanceof ILazyParseableElementType) {
      return ASTFactory.lazy((ILazyParseableElementType)type, text);
    }

    return ASTFactory.leaf(type, text);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getUserDataUnprotected(@NotNull final Key<T> key) {
    if (key == FileContextUtil.CONTAINING_FILE_KEY) return (T)myFile;
    return myUserData != null ? (T)myUserData.get(key) : null;
  }

  @Override
  public <T> void putUserDataUnprotected(@NotNull final Key<T> key, @Nullable final T value) {
    if (key == FileContextUtil.CONTAINING_FILE_KEY) {
      myFile = (PsiFile)value;
      return;
    }
    if (myUserData == null) myUserData = ContainerUtil.newHashMap();
    myUserData.put(key, value);
  }
}

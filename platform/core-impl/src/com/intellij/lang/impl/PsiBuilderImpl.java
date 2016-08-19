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
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class PsiBuilderImpl extends UserDataHolderBase implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  // function stored in PsiBuilderImpl' user data which called during reparse when merge algorithm is not sure what to merge
  public static final Key<TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>>
    CUSTOM_COMPARATOR = Key.create("CUSTOM_COMPARATOR");

  private static final Key<LazyParseableTokensCache> LAZY_PARSEABLE_TOKENS = Key.create("LAZY_PARSEABLE_TOKENS");

  private static TokenSet ourAnyLanguageWhitespaceTokens = TokenSet.EMPTY;

  private final Project myProject;
  private PsiFile myFile;

  private int[] myLexStarts;
  private IElementType[] myLexTypes;
  private int myCurrentLexeme;

  private final MyList myProduction = new MyList();

  private final Lexer myLexer;
  private final TokenSet myWhitespaces;
  private TokenSet myComments;

  private CharTable myCharTable;
  private final CharSequence myText;
  private final CharSequence myLastCommittedText;
  private final char[] myTextArray;
  private boolean myDebugMode;
  private int myLexemeCount;
  private boolean myTokenTypeChecked;
  private ITokenTypeRemapper myRemapper;
  private WhitespaceSkippedCallback myWhitespaceSkippedCallback;

  private final ASTNode myOriginalTree;
  private final MyTreeStructure myParentLightTree;
  private final int myOffset;

  private Map<Key, Object> myUserData;
  private IElementType myCachedTokenType;

  private final LimitedPool<StartMarker> START_MARKERS = new LimitedPool<StartMarker>(2000, new LimitedPool.ObjectFactory<StartMarker>() {
    @NotNull
    @Override
    public StartMarker create() {
      return new StartMarker();
    }

    @Override
    public void cleanup(@NotNull final StartMarker startMarker) {
      startMarker.clean();
    }
  });

  private final LimitedPool<DoneMarker> DONE_MARKERS = new LimitedPool<DoneMarker>(2000, new LimitedPool.ObjectFactory<DoneMarker>() {
    @NotNull
    @Override
    public DoneMarker create() {
      return new DoneMarker();
    }

    @Override
    public void cleanup(@NotNull final DoneMarker doneMarker) {
      doneMarker.clean();
    }
  });
  private static final ArrayFactory<IElementType> myElementTypeArrayFactory = new ArrayFactory<IElementType>() {
    @NotNull
    @Override
    public IElementType[] create(int count) {
      return count == 0 ? IElementType.EMPTY_ARRAY : new IElementType[count];
    }
  };

  public static void registerWhitespaceToken(@NotNull IElementType type) {
    ourAnyLanguageWhitespaceTokens = TokenSet.orSet(ourAnyLanguageWhitespaceTokens, TokenSet.create(type));
  }

  @TestOnly
  public PsiBuilderImpl(@NotNull Project project,
                        PsiFile containingFile,
                        @NotNull ParserDefinition parserDefinition,
                        @NotNull Lexer lexer,
                        CharTable charTable,
                        @NotNull final CharSequence text,
                        @Nullable ASTNode originalTree,
                        @Nullable MyTreeStructure parentLightTree) {
    this(project, containingFile, parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(), lexer, charTable, text,
         originalTree, originalTree == null ? null : originalTree.getText(), parentLightTree, null);
  }

  public PsiBuilderImpl(Project project,
                        PsiFile containingFile,
                        @NotNull TokenSet whiteSpaces,
                        @NotNull TokenSet comments,
                        @NotNull Lexer lexer,
                        CharTable charTable,
                        @NotNull final CharSequence text,
                        @Nullable ASTNode originalTree,
                        @Nullable MyTreeStructure parentLightTree) {
    this(project, containingFile, whiteSpaces, comments, lexer, charTable, text, originalTree, originalTree == null ? null : originalTree.getText(), parentLightTree, null);
  }

  private PsiBuilderImpl(Project project,
                         PsiFile containingFile,
                         @NotNull TokenSet whiteSpaces,
                         @NotNull TokenSet comments,
                         @NotNull Lexer lexer,
                         CharTable charTable,
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

    myWhitespaces = whiteSpaces;
    myComments = comments;
    myCharTable = charTable;
    myOriginalTree = originalTree;
    myLastCommittedText = lastCommittedText;
    if ((originalTree == null) != (lastCommittedText == null)) {
      throw new IllegalArgumentException("originalTree and lastCommittedText must be null/notnull together but got: originalTree=" + originalTree + "; lastCommittedText=" +
                                         (lastCommittedText == null ? null : "'"+StringUtil.first(lastCommittedText, 80, true)+"'"));
    }
    myParentLightTree = parentLightTree;
    myOffset = parentCachingNode instanceof LazyParseableToken ? ((LazyParseableToken)parentCachingNode).getStartOffset() : 0;

    cacheLexemes(parentCachingNode);
  }

  public PsiBuilderImpl(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final Lexer lexer,
                        @NotNull final ASTNode chameleon,
                        @NotNull final CharSequence text) {
    this(project, SharedImplUtil.getContainingFile(chameleon), parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(),
         lexer, SharedImplUtil.findCharTableByTree(chameleon), text,
         Pair.getFirst(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)), Pair.getSecond(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
         null, chameleon);
  }

  public PsiBuilderImpl(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final Lexer lexer,
                        @NotNull final LighterLazyParseableNode chameleon,
                        @NotNull final CharSequence text) {
    this(project, chameleon.getContainingFile(), parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(), lexer,
         chameleon.getCharTable(), text, null, null, ((LazyParseableToken)chameleon).myParentStructure, chameleon);
  }

  private void cacheLexemes(@Nullable Object parentCachingNode) {
    int[] lexStarts = null;
    IElementType[] lexTypes = null;
    int lexemeCount = -1;
    // set this to true to check that re-lexing of lazy parseables produces the same sequence as cached one
    boolean doLexingOptimizationCorrectionCheck = false;

    if (parentCachingNode instanceof LazyParseableToken) {
      final LazyParseableToken parentToken = (LazyParseableToken)parentCachingNode;
      // there are two types of lazy parseable tokens out there: collapsed out of individual tokens or single token that needs to be expanded
      // in first case parent PsiBuilder has all our text lexed so no need to do it again
      int tokenCount = parentToken.myEndIndex - parentToken.myStartIndex;
      if (tokenCount != 1) { // not expand single lazy parseable token case
        lexStarts = new int[tokenCount + 1];
        System.arraycopy(parentToken.myBuilder.myLexStarts, parentToken.myStartIndex, lexStarts, 0, tokenCount);
        int diff = parentToken.myBuilder.myLexStarts[parentToken.myStartIndex];
        for(int i = 0; i < tokenCount; ++i) lexStarts[i] -= diff;
        lexStarts[tokenCount] = myText.length();

        lexTypes = new IElementType[tokenCount];
        System.arraycopy(parentToken.myBuilder.myLexTypes, parentToken.myStartIndex, lexTypes, 0, tokenCount);
        lexemeCount = tokenCount;
      }
      ProgressIndicatorProvider.checkCanceled();

      //noinspection ConstantConditions
      if (!doLexingOptimizationCorrectionCheck && lexemeCount != -1) {
        myLexStarts = lexStarts;
        myLexTypes = lexTypes;
        myLexemeCount = lexemeCount;
        return;
      }
    }
    else if (parentCachingNode instanceof LazyParseableElement) {
      final LazyParseableElement parentElement = (LazyParseableElement)parentCachingNode;
      final LazyParseableTokensCache cachedTokens = parentElement.getUserData(LAZY_PARSEABLE_TOKENS);
      parentElement.putUserData(LAZY_PARSEABLE_TOKENS, null);
      //noinspection ConstantConditions
      if (!doLexingOptimizationCorrectionCheck && cachedTokens != null) {
        myLexStarts = cachedTokens.myLexStarts;
        myLexTypes = cachedTokens.myLexTypes;
        myLexemeCount = myLexTypes.length;
        return;
      }
    }

    int approxLexCount = Math.max(10, myText.length() / 5);

    myLexStarts = new int[approxLexCount];
    myLexTypes = new IElementType[approxLexCount];

    myLexer.start(myText);
    int i = 0;
    int offset = 0;
    while (true) {
      ProgressIndicatorProvider.checkCanceled();
      IElementType type = myLexer.getTokenType();
      if (type == null) break;

      if (i >= myLexTypes.length - 1) {
        resizeLexemes(i * 3 / 2);
      }
      int tokenStart = myLexer.getTokenStart();
      if (tokenStart < offset) {
        final StringBuilder sb = new StringBuilder();
        final IElementType tokenType = myLexer.getTokenType();
        sb.append("Token sequence broken")
          .append("\n  this: '").append(myLexer.getTokenText()).append("' (").append(tokenType).append(':')
          .append(tokenType != null ? tokenType.getLanguage() : null).append(") ").append(tokenStart).append(":")
          .append(myLexer.getTokenEnd());
        if (i > 0) {
          final int prevStart = myLexStarts[i - 1];
          sb.append("\n  prev: '").append(myText.subSequence(prevStart, offset)).append("' (").append(myLexTypes[i - 1]).append(':')
            .append(myLexTypes[i - 1].getLanguage()).append(") ").append(prevStart).append(":").append(offset);
        }
        final int quoteStart = Math.max(tokenStart - 256, 0);
        final int quoteEnd = Math.min(tokenStart + 256, myText.length());
        sb.append("\n  quote: [").append(quoteStart).append(':').append(quoteEnd)
          .append("] '").append(myText.subSequence(quoteStart, quoteEnd)).append('\'');
        LOG.error(sb);
      }
      myLexStarts[i] = offset = tokenStart;
      myLexTypes[i] = type;
      i++;
      myLexer.advance();
    }

    myLexStarts[i] = myText.length();

    myLexemeCount = i;
    clearCachedTokenType();

    //noinspection ConstantConditions
    if (doLexingOptimizationCorrectionCheck && lexemeCount != -1) {
      assert lexemeCount == myLexemeCount;
      for(int j = 0; j < lexemeCount; ++j) {
        if (myLexStarts[j] != lexStarts[j] || myLexTypes[j] != lexTypes[j]) {
          assert false;
        }
      }
      assert myLexStarts[lexemeCount] == lexStarts[lexemeCount];
    }
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
  public LighterASTNode getLatestDoneMarker() {
    int index = myProduction.size() - 1;
    while (index >= 0) {
      ProductionMarker marker = myProduction.get(index);
      if (marker instanceof DoneMarker) return ((DoneMarker)marker).myStart;
      --index;
    }
    return null;
  }

  private abstract static class Node implements LighterASTNode {
    public abstract int hc();
  }

  public abstract static class ProductionMarker extends Node {
    protected int myLexemeIndex;
    protected WhitespacesAndCommentsBinder myEdgeTokenBinder;
    protected ProductionMarker myParent;
    protected ProductionMarker myNext;

    public void clean() {
      myLexemeIndex = 0;
      myParent = myNext = null;
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
  }

  private static class StartMarker extends ProductionMarker implements Marker {
    private PsiBuilderImpl myBuilder;
    private IElementType myType;
    private DoneMarker myDoneMarker;
    private Throwable myDebugAllocationPosition;
    private ProductionMarker myFirstChild;
    private ProductionMarker myLastChild;
    private int myHC = -1;

    private StartMarker() {
      myEdgeTokenBinder = WhitespacesBinders.DEFAULT_LEFT_BINDER;
    }

    @Override
    public void clean() {
      super.clean();
      myBuilder = null;
      myType = null;
      myDoneMarker = null;
      myDebugAllocationPosition = null;
      myFirstChild = myLastChild = null;
      myHC = -1;
      myEdgeTokenBinder = WhitespacesBinders.DEFAULT_LEFT_BINDER;
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
            lexIdx = ((StartMarker)child).myDoneMarker.myLexemeIndex;
          }
          child = child.myNext;
        }

        for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[myDoneMarker.myLexemeIndex]; i++) {
          hc += bufArray != null ? bufArray[i] : buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    @Override
    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemeIndex] + myBuilder.myOffset;
    }

    @Override
    public int getEndOffset() {
      return myBuilder.myLexStarts[myDoneMarker.myLexemeIndex] + myBuilder.myOffset;
    }

    @Override
    public int getEndIndex() {
      return myDoneMarker.myLexemeIndex;
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
      myBuilder.drop(this);
    }

    @Override
    public void rollbackTo() {
      myBuilder.rollbackTo(this);
    }

    @Override
    public void done(@NotNull IElementType type) {
      myType = type;
      myBuilder.done(this);
    }

    @Override
    public void collapse(@NotNull IElementType type) {
      myType = type;
      myBuilder.collapse(this);
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
      myType = type;
      myBuilder.doneBefore(this, before);
    }

    @Override
    public void doneBefore(@NotNull final IElementType type, @NotNull final Marker before, final String errorMessage) {
      StartMarker marker = (StartMarker)before;
      myBuilder.myProduction.add(myBuilder.myProduction.lastIndexOf(marker), new ErrorItem(myBuilder, errorMessage, marker.myLexemeIndex));
      doneBefore(type, before);
    }

    @Override
    public void error(String message) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.error(this, message);
    }

    @Override
    public void errorBefore(final String message, @NotNull final Marker before) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.errorBefore(this, message, before);
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
        myEdgeTokenBinder = left;
      }

      if (right != null) {
        if (myDoneMarker == null) throw new IllegalArgumentException("Cannot set right-edge processor for unclosed marker");
        myDoneMarker.myEdgeTokenBinder = right;
      }
    }

    @Override
    public String toString() {
      if (myBuilder == null) return "<dropped>";
      boolean isDone = myDoneMarker != null;
      CharSequence originalText = myBuilder.getOriginalText();
      int startOffset = getStartOffset() - myBuilder.myOffset;
      int endOffset = isDone ? getEndOffset() - myBuilder.myOffset : myBuilder.getCurrentOffset();
      CharSequence text = originalText.subSequence(startOffset, endOffset);
      return isDone ? text.toString() : text + "...";
    }
  }

  @NotNull
  private Marker precede(final StartMarker marker) {
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Cannot precede dropped or rolled-back marker");
    }
    StartMarker pre = createMarker(marker.myLexemeIndex);
    myProduction.add(idx, pre);
    return pre;
  }

  private abstract static class Token extends Node {
    protected PsiBuilderImpl myBuilder;
    private IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myHC = -1;

    public void clean() {
      myBuilder = null;
      myHC = -1;
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
  }

  private static class TokenNode extends Token implements LighterASTTokenNode {
    @Override
    public String toString() {
      return getText().toString();
    }
  }

  private static class LazyParseableToken extends Token implements LighterLazyParseableNode {
    private MyTreeStructure myParentStructure;
    private StartMarker myParentNode;
    private FlyweightCapableTreeStructure<LighterASTNode> myParsed;
    private int myStartIndex;
    private int myEndIndex;

    @Override
    public void clean() {
      super.clean();
      myParentStructure = null;
      myParentNode = null;
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
  }

  private static class DoneMarker extends ProductionMarker {
    private StartMarker myStart;
    private boolean myCollapse;

    public DoneMarker() {
      myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;
    }

    public DoneMarker(final StartMarker marker, final int currentLexeme) {
      this();
      myLexemeIndex = currentLexeme;
      myStart = marker;
    }

    @Override
    public void clean() {
      super.clean();
      myStart = null;
      myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;
    }

    @Override
    public int hc() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    @NotNull
    @Override
    public IElementType getTokenType() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    @Override
    public int getEndOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    @Override
    public int getStartOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }
  }

  private static class DoneWithErrorMarker extends DoneMarker {
    private String myMessage;

    private DoneWithErrorMarker(@NotNull StartMarker marker, final int currentLexeme, final String message) {
      super(marker, currentLexeme);
      myMessage = message;
    }

    @Override
    public void clean() {
      super.clean();
      myMessage = null;
    }
  }

  private static class ErrorItem extends ProductionMarker {
    private final PsiBuilderImpl myBuilder;
    private String myMessage;

    public ErrorItem(final PsiBuilderImpl builder, final String message, final int idx) {
      myBuilder = builder;
      myMessage = message;
      myLexemeIndex = idx;
      myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;
    }

    @Override
    public void clean() {
      super.clean();
      myMessage = null;
    }

    @Override
    public int hc() {
      return 0;
    }

    @Override
    public int getEndOffset() {
      return myBuilder.myLexStarts[myLexemeIndex] + myBuilder.myOffset;
    }

    @Override
    public int getStartOffset() {
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

  public int rawTokenOffset(int tokenIndex) {
    return myLexStarts[tokenIndex];
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

  private void resizeLexemes(final int newSize) {
    myLexStarts = ArrayUtil.realloc(myLexStarts, newSize+1);
    myLexTypes = ArrayUtil.realloc(myLexTypes, newSize, myElementTypeArrayFactory);
    clearCachedTokenType();
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

    myProduction.add(marker);
    return marker;
  }

  @NotNull
  private StartMarker createMarker(final int lexemeIndex) {
    StartMarker marker = START_MARKERS.alloc();
    marker.myLexemeIndex = lexemeIndex;
    marker.myBuilder = this;

    if (myDebugMode) {
      marker.myDebugAllocationPosition = new Throwable("Created at the following trace.");
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

  private void rollbackTo(@NotNull Marker marker) {
    myCurrentLexeme = ((StartMarker)marker).myLexemeIndex;
    myTokenTypeChecked = true;
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("The marker must be added before rolled back to.");
    }
    myProduction.removeRange(idx, myProduction.size());
    START_MARKERS.recycle((StartMarker)marker);
    clearCachedTokenType();
  }

  /**
   *
   * @return true if there are error elements created and not dropped after marker was created
   */
  public boolean hasErrorsAfter(@NotNull Marker marker) {
    assert marker instanceof StartMarker;
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("The marker must be added before checked for errors.");
    }
    for (int i = idx+1; i < myProduction.size(); ++i) {
      ProductionMarker m = myProduction.get(i);
      if (m instanceof ErrorItem || m instanceof DoneWithErrorMarker) {
        return true;
      }
    }
    return false;
  }

  public void drop(@NotNull Marker marker) {
    final DoneMarker doneMarker = ((StartMarker)marker).myDoneMarker;
    if (doneMarker != null) {
      myProduction.remove(myProduction.lastIndexOf(doneMarker));
      DONE_MARKERS.recycle(doneMarker);
    }
    final boolean removed = myProduction.remove(myProduction.lastIndexOf(marker)) == marker;
    if (!removed) {
      LOG.error("The marker must be added before it is dropped.");
    }
    START_MARKERS.recycle((StartMarker)marker);
  }

  public void error(@NotNull Marker marker, String message) {
    doValidityChecks(marker, null);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, myCurrentLexeme, message);
    boolean tieToTheLeft = isEmpty(((StartMarker)marker).myLexemeIndex, myCurrentLexeme);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  private void errorBefore(@NotNull Marker marker, String message, @NotNull Marker before) {
    doValidityChecks(marker, before);

    @SuppressWarnings("SuspiciousMethodCalls")
    int beforeIndex = myProduction.lastIndexOf(before);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, ((StartMarker)before).myLexemeIndex, message);
    boolean tieToTheLeft = isEmpty(((StartMarker)marker).myLexemeIndex, ((StartMarker)before).myLexemeIndex);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(beforeIndex, doneMarker);
  }

  public void done(@NotNull Marker marker) {
    doValidityChecks(marker, null);

    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myStart = (StartMarker)marker;
    doneMarker.myLexemeIndex = myCurrentLexeme;
    boolean tieToTheLeft = doneMarker.myStart.myType.isLeftBound() &&
                           isEmpty(((StartMarker)marker).myLexemeIndex, myCurrentLexeme);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void doneBefore(@NotNull Marker marker, @NotNull Marker before) {
    doValidityChecks(marker, before);

    @SuppressWarnings("SuspiciousMethodCalls")
    int beforeIndex = myProduction.lastIndexOf(before);

    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myLexemeIndex = ((StartMarker)before).myLexemeIndex;
    doneMarker.myStart = (StartMarker)marker;
    boolean tieToTheLeft = doneMarker.myStart.myType.isLeftBound() &&
                           isEmpty(((StartMarker)marker).myLexemeIndex, ((StartMarker)before).myLexemeIndex);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeTokenBinder = WhitespacesBinders.DEFAULT_RIGHT_BINDER;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(beforeIndex, doneMarker);
  }

  private boolean isEmpty(final int startIdx, final int endIdx) {
    for (int i = startIdx; i < endIdx; i++) {
      final IElementType token = myLexTypes[i];
      if (!whitespaceOrComment(token)) return false;
    }
    return true;
  }

  public void collapse(@NotNull Marker marker) {
    done(marker);
    ((StartMarker)marker).myDoneMarker.myCollapse = true;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void doValidityChecks(@NotNull Marker marker, @Nullable final Marker before) {
    final DoneMarker doneMarker = ((StartMarker)marker).myDoneMarker;
    if (doneMarker != null) {
      LOG.error("Marker already done.");
    }

    if (!myDebugMode) return;

    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Marker has never been added.");
    }

    int endIdx = myProduction.size();
    if (before != null) {
      //noinspection SuspiciousMethodCalls
      endIdx = myProduction.lastIndexOf(before);
      if (endIdx < 0) {
        LOG.error("'Before' marker has never been added.");
      }
      if (idx > endIdx) {
        LOG.error("'Before' marker precedes this one.");
      }
    }

    for (int i = endIdx - 1; i > idx; i--) {
      Object item = myProduction.get(i);
      if (item instanceof StartMarker) {
        StartMarker otherMarker = (StartMarker)item;
        if (otherMarker.myDoneMarker == null) {
          final Throwable debugAllocOther = otherMarker.myDebugAllocationPosition;
          final Throwable debugAllocThis = ((StartMarker)marker).myDebugAllocationPosition;
          if (debugAllocOther != null) {
            Throwable currentTrace = new Throwable();
            ExceptionUtil.makeStackTraceRelative(debugAllocThis, currentTrace).printStackTrace(System.err);
            ExceptionUtil.makeStackTraceRelative(debugAllocOther, currentTrace).printStackTrace(System.err);
          }
          LOG.error("Another not done marker added after this one. Must be done before this.");
        }
      }
    }
  }

  @Override
  public void error(String messageText) {
    final ProductionMarker lastMarker = myProduction.get(myProduction.size() - 1);
    if (lastMarker instanceof ErrorItem && lastMarker.myLexemeIndex == myCurrentLexeme) {
      return;
    }
    myProduction.add(new ErrorItem(this, messageText, myCurrentLexeme));
  }

  @Override
  @NotNull
  public ASTNode getTreeBuilt() {
    try {
      return buildTree();
    }
    finally {
      for (ProductionMarker marker : myProduction) {
        if (marker instanceof StartMarker) {
          START_MARKERS.recycle((StartMarker)marker);
        }
        else if (marker instanceof DoneMarker) {
          DONE_MARKERS.recycle((DoneMarker)marker);
        }
      }
    }
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
    ShallowNodeComparator<ASTNode, LighterASTNode> comparator = new MyComparator(getUserDataUnprotected(CUSTOM_COMPARATOR), treeStructure);

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
    StartMarker rootMarker = (StartMarker)myProduction.get(0);
    if (rootMarker.myFirstChild != null) return rootMarker;

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
      final ProductionMarker item = myProduction.get(i);

      if (curNode == null) LOG.error("Unexpected end of the production");

      item.myParent = curNode;
      if (item instanceof StartMarker) {
        final StartMarker marker = (StartMarker)item;
        marker.myFirstChild = marker.myLastChild = marker.myNext = null;
        curNode.addChild(marker);
        nodes.push(curNode);
        curNode = marker;
        curDepth++;
        if (curDepth > maxDepth) maxDepth = curDepth;
      }
      else if (item instanceof DoneMarker) {
        assertMarkersBalanced(((DoneMarker)item).myStart == curNode, item);
        curNode = nodes.pop();
        curDepth--;
      }
      else if (item instanceof ErrorItem) {
        int curToken = item.myLexemeIndex;
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
    }

    if (myCurrentLexeme < myLexemeCount) {
      final List<IElementType> missed = ContainerUtil.newArrayList(myLexTypes, myCurrentLexeme, myLexemeCount);
      LOG.error("Tokens " + missed + " were not inserted into the tree. " +(myFile != null? myFile.getLanguage()+", ":"")+"Text:\n" + myText);
    }

    if (rootMarker.myDoneMarker.myLexemeIndex < myLexemeCount) {
      final List<IElementType> missed = ContainerUtil.newArrayList(myLexTypes, rootMarker.myDoneMarker.myLexemeIndex, myLexemeCount);
      LOG.error("Tokens " + missed + " are outside of root element \"" + rootMarker.myType + "\". Text:\n" + myText);
    }

    if (myLexStarts.length <= myCurrentLexeme + 1) {
      resizeLexemes(myCurrentLexeme + 1);
    }

    myLexStarts[myCurrentLexeme] = myText.length(); // $ terminating token.;
    myLexStarts[myCurrentLexeme + 1] = 0;
    myLexTypes[myCurrentLexeme] = null;

    assertMarkersBalanced(curNode == rootMarker, curNode);

    checkTreeDepth(maxDepth, rootMarker.getTokenType() instanceof IFileElementType);

    clearCachedTokenType();
    return rootMarker;
  }

  private void assertMarkersBalanced(boolean condition, @Nullable ProductionMarker marker) {
    if (condition) return;

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
      ProductionMarker item = myProduction.get(i);
      if (item instanceof StartMarker) {
        assertMarkersBalanced(((StartMarker)item).myDoneMarker != null, item);
      }

      boolean recursive = item.myEdgeTokenBinder instanceof WhitespacesAndCommentsBinder.RecursiveBinder;
      int prevProductionLexIndex = recursive ? 0 : myProduction.get(i - 1).myLexemeIndex;
      int wsStartIndex = Math.max(item.myLexemeIndex, lastIndex);
      while (wsStartIndex > prevProductionLexIndex && whitespaceOrComment(myLexTypes[wsStartIndex - 1])) wsStartIndex--;
      int wsEndIndex = item.myLexemeIndex;
      while (wsEndIndex < myLexemeCount && whitespaceOrComment(myLexTypes[wsEndIndex])) wsEndIndex++;

      if (wsStartIndex != wsEndIndex) {
        wsTokens.configure(wsStartIndex, wsEndIndex);
        tokenTextGetter.configure(wsStartIndex);
        boolean atEnd = wsStartIndex == 0 || wsEndIndex == myLexemeCount;
        item.myLexemeIndex = wsStartIndex + item.myEdgeTokenBinder.getEdgePosition(wsTokens, atEnd, tokenTextGetter);
        if (recursive) {
          for (int k = i - 1; k > 1; k--) {
            ProductionMarker prev = myProduction.get(k);
            if (prev.myLexemeIndex >= item.myLexemeIndex) {
              prev.myLexemeIndex = item.myLexemeIndex;
            }
            else break;
          }
        }
      }
      else if (item.myLexemeIndex < wsStartIndex) {
        item.myLexemeIndex = wsStartIndex;
      }

      lastIndex = item.myLexemeIndex;
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
    ProductionMarker item = rootMarker.myFirstChild != null ? rootMarker.myFirstChild : rootMarker.myDoneMarker;
    while (true) {
      lexIndex = insertLeaves(lexIndex, item.myLexemeIndex, curNode);

      if (item == rootMarker.myDoneMarker) break;

      if (item instanceof StartMarker) {
        final StartMarker marker = (StartMarker)item;
        if (!marker.myDoneMarker.myCollapse) {
          curMarker = marker;

          final CompositeElement childNode = createComposite(marker);
          curNode.rawAddChildrenWithoutNotifications(childNode);
          curNode = childNode;

          item = marker.myFirstChild != null ? marker.myFirstChild : marker.myDoneMarker;
          continue;
        }
        else {
          lexIndex = collapseLeaves(curNode, marker);
        }
      }
      else if (item instanceof ErrorItem) {
        final CompositeElement errorElement = Factory.createErrorElement(((ErrorItem)item).myMessage);
        curNode.rawAddChildrenWithoutNotifications(errorElement);
      }
      else if (item instanceof DoneMarker) {
        curMarker = (StartMarker)((DoneMarker)item).myStart.myParent;
        curNode = curNode.getTreeParent();
        item = ((DoneMarker)item).myStart;
      }

      item = item.myNext != null ? item.myNext : curMarker.myDoneMarker;
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
    final int end = myLexStarts[startMarker.myDoneMarker.myLexemeIndex];
    final IElementType markerType = startMarker.myType;
    final TreeElement leaf = createLeaf(markerType, start, end);
    if (markerType instanceof ILazyParseableElementType && ((ILazyParseableElementType)markerType).reuseCollapsedTokens() &&
        startMarker.myLexemeIndex < startMarker.myDoneMarker.myLexemeIndex) {
      final int length = startMarker.myDoneMarker.myLexemeIndex - startMarker.myLexemeIndex;
      final int[] relativeStarts = new int[length + 1];
      final IElementType[] types = new IElementType[length];
      for (int i = startMarker.myLexemeIndex; i < startMarker.myDoneMarker.myLexemeIndex; i++) {
        relativeStarts[i - startMarker.myLexemeIndex] = myLexStarts[i] - start;
        types[i - startMarker.myLexemeIndex] = myLexTypes[i];
      }
      relativeStarts[length] = end - start;
      leaf.putUserData(LAZY_PARSEABLE_TOKENS, new LazyParseableTokensCache(relativeStarts, types));
    }
    ast.rawAddChildrenWithoutNotifications(leaf);
    return startMarker.myDoneMarker.myLexemeIndex;
  }

  @NotNull
  private static CompositeElement createComposite(@NotNull StartMarker marker) {
    final IElementType type = marker.myType;
    if (type == TokenType.ERROR_ELEMENT) {
      String message = marker.myDoneMarker instanceof DoneWithErrorMarker ? ((DoneWithErrorMarker)marker.myDoneMarker).myMessage : null;
      return Factory.createErrorElement(message);
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
      if (marker.myType == TokenType.ERROR_ELEMENT && marker.myDoneMarker instanceof DoneWithErrorMarker) {
        return ((DoneWithErrorMarker)marker.myDoneMarker).myMessage;
      }
    }

    return null;
  }

  private static class MyComparator implements ShallowNodeComparator<ASTNode, LighterASTNode> {
    private final TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> custom;
    private final MyTreeStructure myTreeStructure;

    private MyComparator(TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> custom,
                         @NotNull MyTreeStructure treeStructure) {
      this.custom = custom;
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

      if (custom != null) {
        ThreeState customResult = custom.fun(oldNode, newNode, myTreeStructure);

        if (customResult != ThreeState.UNSURE) {
          return customResult;
        }
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
          return ((TreeElement)oldNode).textMatches(token.getText())
                 ? ThreeState.YES
                 : TreeUtil.isCollapsedChameleon(oldNode)
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
        myPool = new LimitedPool<Token>(1000, new LimitedPool.ObjectFactory<Token>() {
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
        myLazyPool = new LimitedPool<LazyParseableToken>(200, new LimitedPool.ObjectFactory<LazyParseableToken>() {
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
      throw new UnsupportedOperationException("Unknown node type: " + node);
    }

    @Override
    @NotNull
    public LighterASTNode prepareForGetChildren(@NotNull final LighterASTNode node) {
      return node;
    }

    private int count;
    private LighterASTNode[] nodes;

    @Override
    public int getChildren(@NotNull final LighterASTNode item, @NotNull final Ref<LighterASTNode[]> into) {
      if (item instanceof LazyParseableToken) {
        final FlyweightCapableTreeStructure<LighterASTNode> tree = ((LazyParseableToken)item).parseContents();
        final LighterASTNode root = tree.getRoot();
        if (root instanceof ProductionMarker) {
          ((ProductionMarker)root).myParent = ((LazyParseableToken)item).myParentNode;
        }
        return tree.getChildren(tree.prepareForGetChildren(root), into);  // todo: set offset shift for kids?
      }

      if (item instanceof Token || item instanceof ErrorItem) return 0;
      StartMarker marker = (StartMarker)item;

      count = 0;
      ProductionMarker child = marker.myFirstChild;
      int lexIndex = marker.myLexemeIndex;
      while (child != null) {
        lexIndex = insertLeaves(lexIndex, child.myLexemeIndex, marker.myBuilder, marker);

        if (child instanceof StartMarker && ((StartMarker)child).myDoneMarker.myCollapse) {
          int lastIndex = ((StartMarker)child).myDoneMarker.myLexemeIndex;
          insertLeaf(child.getTokenType(), marker.myBuilder, child.myLexemeIndex, lastIndex, true, marker);
        }
        else {
          ensureCapacity();
          nodes[count++] = child;
        }

        if (child instanceof StartMarker) {
          lexIndex = ((StartMarker)child).myDoneMarker.myLexemeIndex;
        }
        child = child.myNext;
      }

      insertLeaves(lexIndex, marker.myDoneMarker.myLexemeIndex, marker.myBuilder, marker);
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

      final Token lexeme;
      if (type instanceof ILightLazyParseableElementType) {
        lexeme = myLazyPool.alloc();
        LazyParseableToken lazyParseableToken = (LazyParseableToken)lexeme;
        lazyParseableToken.myParentStructure = this;
        lazyParseableToken.myParentNode = parent;
        lazyParseableToken.myStartIndex = startLexemeIndex;
        lazyParseableToken.myEndIndex = endLexemeIndex;
      }
      else {
        lexeme = myPool.alloc();
      }
      lexeme.myBuilder = builder;
      lexeme.myTokenType = type;
      lexeme.myTokenStart = start;
      lexeme.myTokenEnd = end;
      ensureCapacity();
      nodes[count++] = lexeme;
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
  private TreeElement createLeaf(@NotNull IElementType type, final int start, final int end) {
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

  /**
   * just to make removeRange method available.
   */
  private static class MyList extends ArrayList<ProductionMarker> {
    private static final Field ourElementDataField = ReflectionUtil.getDeclaredField(ArrayList.class, "elementData");

    private Object[] cachedElementData;

    @Override
    protected void removeRange(final int fromIndex, final int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }

    private MyList() {
      super(256);
    }

    @Override
    public int lastIndexOf(final Object o) {
      Object[] data = cachedElementData;
      if (data == null) {
        return super.lastIndexOf(o);
      }
      for (int i = size() - 1; i >= 0; i--) {
        if (data[i] == o) return i;
      }
      return -1;
    }

    @Override
    public void ensureCapacity(final int minCapacity) {
      if (cachedElementData == null || minCapacity >= cachedElementData.length) {
        super.ensureCapacity(minCapacity);
        initCachedField();
      }
    }

    private void initCachedField() {
      if (ourElementDataField == null) return;
      try {
        cachedElementData = (Object[])ourElementDataField.get(this);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
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

  private static class LazyParseableTokensCache {
    final int[] myLexStarts;
    final IElementType[] myLexTypes;

    public LazyParseableTokensCache(int[] lexStarts, IElementType[] lexTypes) {
      myLexStarts = lexStarts;
      myLexTypes = lexTypes;
    }
  }
}

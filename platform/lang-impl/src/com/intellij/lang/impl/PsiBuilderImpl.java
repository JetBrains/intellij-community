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

package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.ASTDiffBuilder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * User: max
 */
public class PsiBuilderImpl extends UserDataHolderBase implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  private Project myProject;

  private int[] myLexStarts;
  private IElementType[] myLexTypes;
  private int myCurrentLexeme;

  private final MyList myProduction = new MyList();

  private final Lexer myLexer;
  private final TokenSet myWhitespaces;
  private TokenSet myComments;

  private CharTable myCharTable;
  private final CharSequence myText;
  private final char[] myTextArray;
  private boolean myDebugMode = false;
  private ASTNode myOriginalTree = null;
  private int myLexemeCount = 0;
  private boolean myTokenTypeChecked;
  private ITokenTypeRemapper myRemapper;

  private static TokenSet ourAnyLanguageWhitespaceTokens = TokenSet.EMPTY;

  private final LimitedPool<StartMarker> START_MARKERS = new LimitedPool<StartMarker>(2000, new LimitedPool.ObjectFactory<StartMarker>() {
    public StartMarker create() {
      return new StartMarker();
    }

    public void cleanup(final StartMarker startMarker) {
      startMarker.clean();
    }
  });

  private final LimitedPool<DoneMarker> DONE_MARKERS = new LimitedPool<DoneMarker>(2000, new LimitedPool.ObjectFactory<DoneMarker>() {
    public DoneMarker create() {
      return new DoneMarker();
    }

    public void cleanup(final DoneMarker doneMarker) {
      doneMarker.clean();
    }
  });

  private static final WhitespacesAndCommentsProcessor DEFAULT_LEFT_EDGE_PROCESSOR = new WhitespacesAndCommentsProcessor() {
    public int process(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      return tokens.size();
    }
  };

  private static final WhitespacesAndCommentsProcessor DEFAULT_RIGHT_EDGE_PROCESSOR = new WhitespacesAndCommentsProcessor() {
    public int process(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      return 0;
    }
  };

  @NonNls private static final String UNBALANCED_MESSAGE =
    "Unbalanced tree. Most probably caused by unbalanced markers. " +
    "Try calling setDebugMode(true) against PsiBuilder passed to identify exact location of the problem";

  public static void registerWhitespaceToken(IElementType type) {
    ourAnyLanguageWhitespaceTokens = TokenSet.orSet(ourAnyLanguageWhitespaceTokens, TokenSet.create(type));
  }

  public PsiBuilderImpl(@NotNull final Project project,
                        @NotNull final Language lang,
                        @NotNull final Lexer lexer,
                        @NotNull final ASTNode chameleon,
                        @NotNull final CharSequence text) {
    myProject = project;

    myText = text;
    myTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    myLexer = lexer;

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
    myWhitespaces = parserDefinition.getWhitespaceTokens();
    myComments = parserDefinition.getCommentTokens();

    myCharTable = SharedImplUtil.findCharTableByTree(chameleon);
    myOriginalTree = chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED);

    cacheLexemes();
  }

  @TestOnly
  public PsiBuilderImpl(@NotNull final Lexer lexer,
                        @NotNull final TokenSet whitespaces,
                        @NotNull final TokenSet comments,
                        @NotNull final CharSequence text) {
    myProject = null;

    myText = text;
    myTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    myLexer = lexer;
    myWhitespaces = whitespaces;
    myComments = comments;

    myCharTable = null;
    myOriginalTree = null;

    cacheLexemes();
  }

  @TestOnly
  public void setOriginalTree(final ASTNode originalTree) {
    myOriginalTree = originalTree;
    myCharTable = SharedImplUtil.findCharTableByTree(originalTree);
  }

  private void cacheLexemes() {
    int approxLexCount = Math.max(10, myText.length() / 5);

    myLexStarts = new int[approxLexCount];
    myLexTypes = new IElementType[approxLexCount];

    myLexer.start(myText);
    int i = 0;
    while (true) {
      IElementType type = myLexer.getTokenType();
      if (type == null) break;

      if (i >= myLexTypes.length - 1) {
        resizeLexemes(i * 3 / 2);
      }
      myLexStarts[i] = myLexer.getTokenStart();
      myLexTypes[i] = type;
      i++;
      myLexer.advance();
    }

    myLexStarts[i] = myText.length();

    myLexemeCount = i;
  }

  public Project getProject() {
    return myProject;
  }

  public void enforceCommentTokens(TokenSet tokens) {
    myComments = tokens;
  }

  public @Nullable LighterASTNode getLatestDoneMarker() {
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

  private abstract static class ProductionMarker extends Node {
    public int myLexemeIndex;
    public WhitespacesAndCommentsProcessor myEdgeProcessor;
    public ProductionMarker next;

    public void clean() {
      myLexemeIndex = 0;
      next = null;
    }
  }

  private static class StartMarker extends ProductionMarker implements Marker {
    public PsiBuilderImpl myBuilder;
    public IElementType myType;
    public DoneMarker myDoneMarker;
    public Throwable myDebugAllocationPosition;
    public ProductionMarker firstChild;
    public ProductionMarker lastChild;
    private int myHC = -1;

    private StartMarker() {
      myEdgeProcessor = DEFAULT_LEFT_EDGE_PROCESSOR;
    }

    public void clean() {
      super.clean();
      myBuilder = null;
      myType = null;
      myDoneMarker = null;
      myDebugAllocationPosition = null;
      firstChild = null;
      lastChild = null;
      myHC = -1;
      myEdgeProcessor = DEFAULT_LEFT_EDGE_PROCESSOR;
    }

    public int hc() {
      if (myHC == -1) {
        PsiBuilderImpl builder = myBuilder;
        int hc = 0;
        final CharSequence buf = builder.myText;
        final char[] bufArray = builder.myTextArray;
        ProductionMarker child = firstChild;
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
          child = child.next;
        }

        for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[myDoneMarker.myLexemeIndex]; i++) {
          hc += bufArray != null ? bufArray[i]:buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemeIndex];
    }

    public int getEndOffset() {
      return myBuilder.myLexStarts[myDoneMarker.myLexemeIndex];
    }

    public void addChild(ProductionMarker node) {
      if (firstChild == null) {
        firstChild = node;
        lastChild = node;
      }
      else {
        lastChild.next = node;
        lastChild = node;
      }
    }

    public Marker precede() {
      return myBuilder.precede(this);
    }

    public void drop() {
      myBuilder.drop(this);
    }

    public void rollbackTo() {
      myBuilder.rollbackTo(this);
    }

    public void done(IElementType type) {
      myType = type;
      myBuilder.done(this);
    }

    public void collapse(IElementType type) {
      myType = type;
      myBuilder.collapse(this);
    }

    public void doneBefore(IElementType type, Marker before) {
      myType = type;
      myBuilder.doneBefore(this, before);
    }

    public void doneBefore(final IElementType type, final Marker before, final String errorMessage) {
      final StartMarker marker = (StartMarker)before;
      myBuilder.myProduction.add(myBuilder.myProduction.lastIndexOf(marker), new ErrorItem(myBuilder, errorMessage, marker.myLexemeIndex));
      doneBefore(type, before);
    }

    public void error(String message) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.error(this, message);
    }

    public void errorBefore(final String message, final Marker before) {
      myType = TokenType.ERROR_ELEMENT;
      myBuilder.errorBefore(this, message, before);
    }

    public IElementType getTokenType() {
      return myType;
    }

    public void setCustomEdgeProcessors(final WhitespacesAndCommentsProcessor left, final WhitespacesAndCommentsProcessor right) {
      if (left != null) {
        myEdgeProcessor = left;
      }

      if (right != null) {
        if (myDoneMarker == null) throw new IllegalArgumentException("Cannot set right-edge processor for unclosed marker");
        myDoneMarker.myEdgeProcessor = right;
      }
    }
  }

  private Marker precede(final StartMarker marker) {
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Cannot precede dropped or rolled-back marker");
    }
    StartMarker pre = createMarker(marker.myLexemeIndex);
    myProduction.add(idx, pre);
    return pre;
  }

  private static class Token extends Node {
    public PsiBuilderImpl myBuilder;
    public IElementType myTokenType;
    public int myTokenStart;
    public int myTokenEnd;
    public int myHC = -1;

    public void clean() {
      myBuilder = null;
      myHC = -1;
    }

    public int hc() {
      if (myHC == -1) {
        int hc = 0;
        if (myTokenType instanceof TokenWrapper){
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

    public int getEndOffset() {
      return myTokenEnd;
    }

    public int getStartOffset() {
      return myTokenStart;
    }

    public CharSequence getText() {
      if (myTokenType instanceof TokenWrapper) {
        return ((TokenWrapper)myTokenType).getValue();
      }

      return myBuilder.myText.subSequence(myTokenStart, myTokenEnd);
    }

    public IElementType getTokenType() {
      return myTokenType;
    }
  }

  private static class DoneMarker extends ProductionMarker {
    public StartMarker myStart;
    public boolean myCollapse = false;

    public DoneMarker() {
      myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;
    }

    public DoneMarker(final StartMarker marker, final int currentLexeme) {
      this();
      myLexemeIndex = currentLexeme;
      myStart = marker;
    }

    public void clean() {
      super.clean();
      myStart = null;
      myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;
    }

    public int hc() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public IElementType getTokenType() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public int getEndOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public int getStartOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }
  }

  private static class DoneWithErrorMarker extends DoneMarker {
    public String myMessage;

    public DoneWithErrorMarker(final StartMarker marker, final int currentLexeme, final String message) {
      super(marker, currentLexeme);
      myMessage = message;
    }

    public void clean() {
      super.clean();
      myMessage = null;
    }
  }

  private static class ErrorItem extends ProductionMarker {
    String myMessage;
    private final PsiBuilderImpl myBuilder;

    public ErrorItem(PsiBuilderImpl builder, final String message, int idx) {
      myBuilder = builder;
      myLexemeIndex = idx;
      myMessage = message;
      myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;
    }

    public int hc() {
      return 0;
    }

    public int getEndOffset() {
      return myBuilder.myLexStarts[myLexemeIndex];
    }

    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemeIndex];
    }

    public IElementType getTokenType() {
      return TokenType.ERROR_ELEMENT;
    }

    public void clean() {
      super.clean();
      myMessage = null;
    }
  }

  public CharSequence getOriginalText() {
    return myText;
  }

  public IElementType getTokenType() {
    if (eof()) return null;

    if (myRemapper != null) {
      IElementType type = myLexTypes[myCurrentLexeme];
      type = myRemapper.filter(type, myLexStarts[myCurrentLexeme], myLexStarts[myCurrentLexeme + 1], myLexer.getBufferSequence());
      myLexTypes[myCurrentLexeme] = type; // filter may have changed the type
      return type;
    }
    return myLexTypes[myCurrentLexeme];
  }

  public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
    myRemapper = remapper;
  }

  public void advanceLexer() {
    if (eof()) return;

    if (!myTokenTypeChecked) {
      LOG.assertTrue(eof(), "Probably a bug: eating token without its type checking");
    }
    myTokenTypeChecked = false;
    myCurrentLexeme++;
  }

  private void skipWhitespace() {
    while (myCurrentLexeme < myLexemeCount && whitespaceOrComment(myLexTypes[myCurrentLexeme])) myCurrentLexeme++;
  }

  public int getCurrentOffset() {
    if (eof()) return getOriginalText().length();
    return myLexStarts[myCurrentLexeme];
  }

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
    int count = Math.min(newSize, myLexTypes.length);
    int[] newStarts = new int[newSize + 1];
    System.arraycopy(myLexStarts, 0, newStarts, 0, count);
    myLexStarts = newStarts;

    IElementType[] newTypes = new IElementType[newSize];
    System.arraycopy(myLexTypes, 0, newTypes, 0, count);
    myLexTypes = newTypes;
  }

  private boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.contains(token) || myComments.contains(token);
  }

  public Marker mark() {
    if (!myProduction.isEmpty()) {
      skipWhitespace();
    }
    StartMarker marker = createMarker(myCurrentLexeme);

    myProduction.add(marker);
    return marker;
  }

  private StartMarker createMarker(final int lexemeIndex) {
    StartMarker marker = START_MARKERS.alloc();
    marker.myLexemeIndex = lexemeIndex;
    marker.myBuilder = this;

    if (myDebugMode) {
      marker.myDebugAllocationPosition = new Throwable("Created at the following trace.");
    }
    return marker;
  }

  public final boolean eof() {
    markTokenTypeChecked();
    skipWhitespace();
    return myCurrentLexeme >= myLexemeCount;
  }

  private void markTokenTypeChecked() {
    myTokenTypeChecked = true;
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void rollbackTo(Marker marker) {
    myCurrentLexeme = ((StartMarker)marker).myLexemeIndex;
    markTokenTypeChecked();
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("The marker must be added before rolled back to.");
    }
    myProduction.removeRange(idx, myProduction.size());
    START_MARKERS.recycle((StartMarker)marker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void drop(Marker marker) {
    final boolean removed = myProduction.remove(myProduction.lastIndexOf(marker)) == marker;
    if (!removed) {
      LOG.error("The marker must be added before it is dropped.");
    }
    START_MARKERS.recycle((StartMarker)marker);
  }

  public void error(Marker marker, String message) {
    doValidityChecks(marker, null);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, myCurrentLexeme, message);
    boolean tieToTheLeft = isEmpty(((StartMarker)marker).myLexemeIndex, myCurrentLexeme);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void errorBefore(Marker marker, String message, Marker before) {
    doValidityChecks(marker, before);

    int beforeIndex = myProduction.lastIndexOf(before);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, ((StartMarker)before).myLexemeIndex, message);
    boolean tieToTheLeft = isEmpty(((StartMarker)marker).myLexemeIndex, ((StartMarker)before).myLexemeIndex);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(beforeIndex, doneMarker);
  }

  public void done(final Marker marker) {
    doValidityChecks(marker, null);

    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myStart = (StartMarker)marker;
    doneMarker.myLexemeIndex = myCurrentLexeme;
    boolean tieToTheLeft = doneMarker.myStart.myType.isLeftBound() &&
                           isEmpty(((StartMarker)marker).myLexemeIndex, myCurrentLexeme);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void doneBefore(Marker marker, Marker before) {
    doValidityChecks(marker, before);

    int beforeIndex = myProduction.lastIndexOf(before);

    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myLexemeIndex = ((StartMarker)before).myLexemeIndex;
    doneMarker.myStart = (StartMarker)marker;
    boolean tieToTheLeft = doneMarker.myStart.myType.isLeftBound() &&
                           isEmpty(((StartMarker)marker).myLexemeIndex, ((StartMarker)before).myLexemeIndex);
    if (tieToTheLeft) ((StartMarker)marker).myEdgeProcessor = DEFAULT_RIGHT_EDGE_PROCESSOR;

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

  public void collapse(final Marker marker) {
    done(marker);
    ((StartMarker)marker).myDoneMarker.myCollapse = true;
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SuspiciousMethodCalls"})
  private void doValidityChecks(final Marker marker, @Nullable final Marker before) {
    if (!myDebugMode) return;

    final DoneMarker doneMarker = ((StartMarker)marker).myDoneMarker;
    if (doneMarker != null) {
      LOG.error("Marker already done.");
    }
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Marker has never been added.");
    }

    int endIdx = myProduction.size();
    if (before != null) {
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
            debugAllocThis.printStackTrace(System.err);
            debugAllocOther.printStackTrace(System.err);
          }
          LOG.error("Another not done marker added after this one. Must be done before this.");
        }
      }
    }
  }

  public void error(String messageText) {
    final ProductionMarker lastMarker = myProduction.get(myProduction.size() - 1);
    if (lastMarker instanceof ErrorItem && lastMarker.myLexemeIndex == myCurrentLexeme) {
      return;
    }
    myProduction.add(new ErrorItem(this, messageText, myCurrentLexeme));
  }

  public ASTNode getTreeBuilt() {
    try {
      final StartMarker rootMarker = prepareLightTree();

      if (myOriginalTree != null) {
        merge(myOriginalTree, rootMarker);
        throw new BlockSupport.ReparsedSuccessfullyException();
      }
      else {
        final ASTNode rootNode = createRootAST(rootMarker);

        bind((CompositeElement)rootNode, rootMarker);

        return rootNode;
      }
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

  public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    final StartMarker rootMarker = prepareLightTree();
    return new MyTreeStructure(rootMarker);
  }

  private ASTNode createRootAST(final StartMarker rootMarker) {
    final ASTNode rootNode = createComposite(rootMarker);
    if (myCharTable == null) {
      myCharTable = rootNode instanceof FileElement ? ((FileElement)rootNode).getCharTable() : new CharTableImpl();
    }
    if (!(rootNode instanceof FileElement)) {
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);
    }
    return rootNode;
  }

  private static class MyBuilder implements DiffTreeChangeBuilder<ASTNode, LighterASTNode> {
    private final ASTDiffBuilder myDelegate;
    private final ASTConverter myConverter;

    public MyBuilder(PsiFileImpl file, StartMarker rootNode) {
      myDelegate = new ASTDiffBuilder(file);
      myConverter = new ASTConverter(rootNode);
    }

    public void nodeDeleted(@NotNull final ASTNode oldParent, @NotNull final ASTNode oldNode) {
      myDelegate.nodeDeleted(oldParent, oldNode);
    }

    public void nodeInserted(@NotNull final ASTNode oldParent, @NotNull final LighterASTNode newNode, final int pos) {
      myDelegate.nodeInserted(oldParent, myConverter.convert((Node)newNode), pos);
    }

    public void nodeReplaced(@NotNull final ASTNode oldChild, @NotNull final LighterASTNode newChild) {
      myDelegate.nodeReplaced(oldChild, myConverter.convert((Node)newChild));
    }

    public TreeChangeEvent getEvent() {
      return myDelegate.getEvent();
    }
  }

  private void merge(final ASTNode oldNode, final StartMarker newNode) {
    final PsiFileImpl file = (PsiFileImpl)oldNode.getPsi().getContainingFile();
    final PomModel model = PomManager.getModel(file.getProject());

    try {
      model.runTransaction(new PomTransactionBase(file, model.getModelAspect(TreeAspect.class)) {
        public PomModelEvent runInner() throws IncorrectOperationException {
          final MyBuilder builder = new MyBuilder(file, newNode);
          DiffTree.diff(new ASTStructure(oldNode), new MyTreeStructure(newNode), new MyComparator(), builder);
          file.subtreeChanged();

          return new TreeAspectEvent(model, builder.getEvent());
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    catch (Throwable e) {
      throw new RuntimeException(UNBALANCED_MESSAGE, e);
    }
  }

  private StartMarker prepareLightTree() {
    final StartMarker rootMarker = (StartMarker)myProduction.get(0);

    markTokenTypeChecked();

    balanceWhiteSpaces();

    rootMarker.firstChild = rootMarker.lastChild = rootMarker.next = null;
    StartMarker curNode = rootMarker;
    final Stack<StartMarker> nodes = new Stack<StartMarker>();
    nodes.push(rootMarker);

    int lastErrorIndex = -1;
    for (int i = 1; i < myProduction.size(); i++) {
      final ProductionMarker item = myProduction.get(i);

      if (curNode == null) LOG.error("Unexpected end of the production");

      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        marker.firstChild = marker.lastChild = marker.next = null;
        curNode.addChild(marker);
        nodes.push(curNode);
        curNode = marker;
      }
      else if (item instanceof DoneMarker) {
        curNode = nodes.pop();
      }
      else if (item instanceof ErrorItem) {
        int curToken = item.myLexemeIndex;
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
    }

    final boolean allTokensInserted = myCurrentLexeme >= myLexemeCount;
    if (!allTokensInserted) {
      LOG.error("Not all of the tokens inserted to the tree, parsed text:\n" + myText);
    }

    if (myLexStarts.length <= myCurrentLexeme + 1) {
      resizeLexemes(myCurrentLexeme + 1);
    }

    myLexStarts[myCurrentLexeme] = myText.length(); // $ terminating token.;
    myLexStarts[myCurrentLexeme + 1] = 0;
    myLexTypes[myCurrentLexeme] = null;

    LOG.assertTrue(curNode == rootMarker, UNBALANCED_MESSAGE);
    return rootMarker;
  }

  private void balanceWhiteSpaces() {
    for (int i = 1; i < myProduction.size() - 1; i++) {
      final ProductionMarker item = myProduction.get(i);

      if (item instanceof StartMarker && ((StartMarker)item).myDoneMarker == null) {
        LOG.error(UNBALANCED_MESSAGE);
      }

      final int prevProductionLexIndex = myProduction.get(i - 1).myLexemeIndex;
      int idx = item.myLexemeIndex;
      while (idx > prevProductionLexIndex && whitespaceOrComment(myLexTypes[idx - 1])) idx--;
      final int wsStartIndex = idx;

      int wsEndIndex = item.myLexemeIndex;
      while (wsEndIndex < myLexemeCount && whitespaceOrComment(myLexTypes[wsEndIndex])) wsEndIndex++;

      final List<IElementType> wsTokens = CollectionFactory.arrayList(myLexTypes, wsStartIndex, wsEndIndex);
      final boolean atEnd = (wsStartIndex == 0 || wsEndIndex == myLexemeCount);
      final WhitespacesAndCommentsProcessor.TokenTextGetter getter = new WhitespacesAndCommentsProcessor.TokenTextGetter() {
        public CharSequence get(final int i) {
          return myText.subSequence(myLexStarts[wsStartIndex + i], myLexStarts[wsStartIndex + i + 1]);
        }
      };
      item.myLexemeIndex = wsStartIndex + item.myEdgeProcessor.process(wsTokens, atEnd, getter);
    }
  }

  private void bind(CompositeElement ast, StartMarker marker) {
    bind(ast, marker, marker.myLexemeIndex);
  }

  private int bind(CompositeElement ast, StartMarker marker, int lexIndex) {
    ProductionMarker child = marker.firstChild;
    while (child != null) {
      if (child instanceof StartMarker) {
        final StartMarker childMarker = (StartMarker)child;

        lexIndex = insertLeafs(lexIndex, childMarker.myLexemeIndex, ast);

        if (!childMarker.myDoneMarker.myCollapse) {
          CompositeElement childNode = createComposite(childMarker);
          ast.rawAddChildren(childNode);
          lexIndex = bind(childNode, childMarker, lexIndex);
        }
        else {
          lexIndex = collapseLeafs(ast, childMarker);
        }

        lexIndex = insertLeafs(lexIndex, childMarker.myDoneMarker.myLexemeIndex, ast);
      }
      else if (child instanceof ErrorItem) {
        lexIndex = insertLeafs(lexIndex, child.myLexemeIndex, ast);
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)child).myMessage);
        ast.rawAddChildren(errorElement);
      }

      child = child.next;
    }

    return insertLeafs(lexIndex, marker.myDoneMarker.myLexemeIndex, ast);
  }

  private int insertLeafs(int curToken, int lastIdx, final CompositeElement curNode) {
    lastIdx = Math.min(lastIdx, myLexemeCount);
    while (curToken < lastIdx) {
      final int start = myLexStarts[curToken];
      final int end = myLexStarts[curToken + 1];
      if (start < end || myLexTypes[curToken] instanceof ILeafElementType) { // Empty token. Most probably a parser directive like indent/dedent in Python
        final IElementType type = myLexTypes[curToken];
        final TreeElement leaf = createLeaf(type, start, end);
        curNode.rawAddChildren(leaf);
      }
      curToken++;
    }

    return curToken;
  }

  private int collapseLeafs(CompositeElement ast, StartMarker startMarker) {
    final int start = myLexStarts[startMarker.myLexemeIndex];
    final int end = myLexStarts[startMarker.myDoneMarker.myLexemeIndex];
    final TreeElement leaf = createLeaf(startMarker.myType, start, end);
    ast.rawAddChildren(leaf);
    return startMarker.myDoneMarker.myLexemeIndex;
  }

  private static CompositeElement createComposite(final StartMarker marker) {
    final IElementType type = marker.myType;
    if (type == TokenType.ERROR_ELEMENT) {
      CompositeElement childNode = new PsiErrorElementImpl();
      if (marker.myDoneMarker instanceof DoneWithErrorMarker) {
        ((PsiErrorElementImpl)childNode).setErrorDescription(((DoneWithErrorMarker)marker.myDoneMarker).myMessage);
      }
      return childNode;
    }

    if (type == null) {
      throw new RuntimeException(UNBALANCED_MESSAGE);
    }

    return ASTFactory.composite(type);
  }

  @Nullable
  public static String getErrorMessage(LighterASTNode node) {
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
    public ThreeState deepEqual(final ASTNode oldNode, final LighterASTNode newNode) {
      boolean oldIsErrorElement = oldNode instanceof PsiErrorElement;
      boolean newIsErrorElement = newNode.getTokenType() == TokenType.ERROR_ELEMENT;
      if (oldIsErrorElement != newIsErrorElement) return ThreeState.NO;
      if (oldIsErrorElement && newIsErrorElement) {
        final PsiErrorElement e1 = (PsiErrorElement)oldNode;
        return Comparing.equal(e1.getErrorDescription(), getErrorMessage(newNode)) ? ThreeState.UNSURE : ThreeState.NO;
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
          return ((LeafElement)oldNode).textMatches(token.getText())
                 ? ThreeState.YES
                 : ThreeState.NO;
        }

        if (oldNode.getElementType() instanceof ILazyParseableElementType && type instanceof ILazyParseableElementType ||
            oldNode.getElementType() instanceof CustomParsingType && type instanceof CustomParsingType) {
          return ((TreeElement)oldNode).textMatches(token.getText())
                 ? ThreeState.YES
                 : ThreeState.NO;
        }
      }

      return ThreeState.UNSURE;
    }

    public boolean typesEqual(final ASTNode n1, final LighterASTNode n2) {
      if (n1 instanceof PsiWhiteSpaceImpl) {
        return ourAnyLanguageWhitespaceTokens.contains(n2.getTokenType()) ||
               n2 instanceof Token && ((Token)n2).myBuilder.myWhitespaces.contains(n2.getTokenType());
      }

      return derefToken(n1.getElementType()) == derefToken(n2.getTokenType());
    }

    public static IElementType derefToken(IElementType probablyWrapper) {
      if (probablyWrapper instanceof TokenWrapper) {
        return derefToken(((TokenWrapper)probablyWrapper).getDelegate());
      }
      return probablyWrapper;
    }

    public boolean hashCodesEqual(final ASTNode n1, final LighterASTNode n2) {
      if (n1 instanceof LeafElement && n2 instanceof Token) {
        if (n1 instanceof ForeignLeafPsiElement && n2.getTokenType() instanceof ForeignLeafType) {
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

  private class MyTreeStructure implements FlyweightCapableTreeStructure<LighterASTNode> {
    private final LimitedPool<Token> myPool = new LimitedPool<Token>(1000, new LimitedPool.ObjectFactory<Token>() {
      public void cleanup(final Token token) {
        token.clean();
      }

      public Token create() {
        return new Token();
      }
    });

    private final StartMarker myRoot;

    public MyTreeStructure(final StartMarker root) {
      myRoot = root;
    }

    @NotNull
    public LighterASTNode prepareForGetChildren(@NotNull final LighterASTNode o) {
      return o;
    }

    @NotNull
    public LighterASTNode getRoot() {
      return myRoot;
    }

    public void disposeChildren(final LighterASTNode[] nodes, final int count) {
      for (int i = 0; i < count; i++) {
        LighterASTNode node = nodes[i];
        if (node instanceof Token) {
          myPool.recycle((Token)node);
        }
      }
    }

    private int count;

    public int getChildren(@NotNull final LighterASTNode item, @NotNull final Ref<LighterASTNode[]> into) {
      if (item instanceof Token || item instanceof ErrorItem) return 0;
      StartMarker marker = (StartMarker)item;

      count = 0;

      ProductionMarker child = marker.firstChild;
      ProductionMarker prevChild = null;
      int lexIndex = marker.myLexemeIndex;
      while (child != null) {
        lexIndex = insertLeafs(lexIndex, child.myLexemeIndex, into);

        if (child instanceof StartMarker && ((StartMarker)child).myDoneMarker.myCollapse) {
          final int start = myLexStarts[child.myLexemeIndex];
          final int end = myLexStarts[((StartMarker)child).myDoneMarker.myLexemeIndex];
          insertLeaf(into, start, end, child.getTokenType());

          if (prevChild != null) prevChild.next = child.next;
          if (marker.firstChild == child) marker.firstChild = child.next;
          if (marker.lastChild == child) marker.lastChild = prevChild;
        }
        else {
          ensureCapacity(into);
          into.get()[count++] = child;
          prevChild = child;
        }

        if (child instanceof StartMarker) {
          lexIndex = ((StartMarker)child).myDoneMarker.myLexemeIndex;
        }
        child = child.next;
      }
      insertLeafs(lexIndex, marker.myDoneMarker.myLexemeIndex, into);

      return count;
    }

    private void ensureCapacity(final Ref<LighterASTNode[]> into) {
      LighterASTNode[] old = into.get();
      if (old == null) {
        old = new LighterASTNode[10];
        into.set(old);
      }
      else if (count >= old.length) {
        LighterASTNode[] newStore = new LighterASTNode[count * 3 / 2];
        System.arraycopy(old, 0, newStore, 0, count);
        into.set(newStore);
      }
    }

    private int insertLeafs(int curToken, int lastIdx, Ref<LighterASTNode[]> into) {
      lastIdx = Math.min(lastIdx, myLexemeCount);
      while (curToken < lastIdx) {
        final int start = myLexStarts[curToken];
        final int end = myLexStarts[curToken + 1];
        final IElementType type = myLexTypes[curToken];
        if (start < end || type instanceof ILeafElementType) { // Empty token. Most probably a parser directive like indent/dedent in Python
          insertLeaf(into, start, end, type);
        }
        curToken++;
      }
      return curToken;
    }

    private void insertLeaf(Ref<LighterASTNode[]> into, int start, int end, IElementType type) {
      Token lexeme = myPool.alloc();
      lexeme.myBuilder = PsiBuilderImpl.this;
      lexeme.myTokenType = type;
      lexeme.myTokenStart = start;
      lexeme.myTokenEnd = end;
      ensureCapacity(into);
      into.get()[count++] = lexeme;
    }
  }

  private static class ASTConverter implements Convertor<Node, ASTNode> {
    private final StartMarker myRoot;

    public ASTConverter(final StartMarker root) {
      myRoot = root;
    }

    public ASTNode convert(final Node n) {
      if (n instanceof Token) {
        final Token token = (Token)n;
        return token.myBuilder.createLeaf(token.getTokenType(), token.myTokenStart, token.myTokenEnd);
      }
      else if (n instanceof ErrorItem) {
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)n).myMessage);
        return errorElement;
      }
      else {
        final StartMarker startMarker = (StartMarker)n;
        final CompositeElement composite = n == myRoot ? (CompositeElement)myRoot.myBuilder.createRootAST(myRoot) : createComposite(startMarker);
        startMarker.myBuilder.bind(composite, startMarker);
        return composite;
      }
    }
  }

  public void setDebugMode(boolean dbgMode) {
    myDebugMode = dbgMode;
  }

  public Lexer getLexer() {
    return myLexer;
  }

  @NotNull
  private TreeElement createLeaf(final IElementType type, final int start, final int end) {
    CharSequence text = myCharTable.intern(myText, start, end);
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof CustomParsingType) {
      return (TreeElement)((CustomParsingType)type).parse(text, myCharTable);
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
    private static final Field ourElementDataField;
    static {
      Field f;
      try {
        f = ArrayList.class.getDeclaredField("elementData");
        f.setAccessible(true);
      } catch(NoSuchFieldException e) {
        // IBM J9 does not have the field
        f = null;
      }
      ourElementDataField = f;
    }

    private Object[] cachedElementData;

    public void removeRange(final int fromIndex, final int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }

    MyList() {
      super(256);
    }

    public int lastIndexOf(final Object o) {
      if (cachedElementData == null) return super.lastIndexOf(o);
      for (int i = size()-1; i >= 0; i--)
        if (cachedElementData[i]==o) return i;
      return -1;
    }

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
}

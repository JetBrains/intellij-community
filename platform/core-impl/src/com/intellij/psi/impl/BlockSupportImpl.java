// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.templateLanguages.ITemplateDataElementType;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import org.jetbrains.annotations.*;

import java.util.List;

@ApiStatus.Internal
public final class BlockSupportImpl extends BlockSupport {
  private static final Logger LOG = Logger.getInstance(BlockSupportImpl.class);

  @Override
  public void reparseRange(@NotNull PsiFile file,
                           int startOffset,
                           int endOffset,
                           @NotNull CharSequence newText) throws IncorrectOperationException {
    LOG.assertTrue(file.isValid());
    PsiFileImpl psiFile = (PsiFileImpl)file;
    Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
    document.replaceString(startOffset, endOffset, newText);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
  }

  @Override
  public @NotNull DiffLog reparseRange(@NotNull PsiFile file,
                                       @NotNull FileASTNode oldFileNode,
                                       @NotNull TextRange changedPsiRange,
                                       @NotNull CharSequence newFileText,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull CharSequence lastCommittedText) {
    return reparse(file, oldFileNode, changedPsiRange, newFileText, indicator, lastCommittedText).log;
  }

  @ApiStatus.Internal
  public static final class ReparseResult {
    public final @NotNull DiffLog log;
    public final @NotNull ASTNode oldRoot;
    public final @NotNull ASTNode newRoot;

    ReparseResult(@NotNull DiffLog log, @NotNull ASTNode oldRoot, @NotNull ASTNode newRoot) {
      this.log = log;
      this.oldRoot = oldRoot;
      this.newRoot = newRoot;
    }
  }

  // return diff log, old node to replace, new node (in dummy file)
  // MUST call .close() on the returned result
  @ApiStatus.Internal
  public static @NotNull ReparseResult reparse(
    @NotNull PsiFile file,
    @NotNull FileASTNode oldFileNode,
    @NotNull TextRange changedPsiRange,
    @NotNull CharSequence newFileText,
    @NotNull ProgressIndicator indicator,
    @NotNull CharSequence lastCommittedText) {
    PsiFileImpl fileImpl = (PsiFileImpl)file;

    Couple<ASTNode> rawReparseResult = findReparseableNodeAndReparseIt(fileImpl, oldFileNode, changedPsiRange, newFileText);
    if (rawReparseResult == null) {
      return makeFullParse(fileImpl, oldFileNode, newFileText, indicator, lastCommittedText);
    }
    ASTNode oldRoot = rawReparseResult.first;
    ASTNode newRoot = rawReparseResult.second;
    DiffLog diffLog = mergeTrees(fileImpl, oldRoot, newRoot, indicator, lastCommittedText);
    return new ReparseResult(diffLog, oldRoot, newRoot);
  }


  /**
   * Finds the deepest AST node that can be reparsed incrementally, tries reparsing it, and returns the result as a pair
   * of this node and the corresponding new node, or null if reparse can't be performed.
   *
   * @param file            the PsiFile being reparsed with the new underlying AST
   * @param oldFileNode     the old AST node of the file
   * @param changedPsiRange the changed range in the file
   * @param newFileText     new file text
   *
   * @return Pair (old reparseable node, new replacement node)
   *         or {@code null} if reparse can't be done.
   */
  @VisibleForTesting
  public static @Nullable Couple<ASTNode> findReparseableNodeAndReparseIt(@NotNull PsiFileImpl file,
                                                                          @NotNull FileASTNode oldFileNode,
                                                                          @NotNull TextRange changedPsiRange,
                                                                          @NotNull CharSequence newFileText) {
    if (isTooDeep(file)) {
      return null;
    }

    Reparser reparser = new Reparser(file, oldFileNode, newFileText);

    boolean isTemplateFile = oldFileNode.getElementType() instanceof ITemplateDataElementType;

    ASTNode leafAtStart = oldFileNode.findLeafElementAt(Math.max(0, changedPsiRange.getStartOffset() - 1));
    ASTNode leafAtEnd = oldFileNode.findLeafElementAt(Math.min(changedPsiRange.getEndOffset(), oldFileNode.getTextLength() - 1));
    ASTNode node = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : oldFileNode;

    TextRange startLeafRange = leafAtStart == null ? null : leafAtStart.getTextRange();
    TextRange endLeafRange = leafAtEnd == null ? null : leafAtEnd.getTextRange();

    IElementType startLeafType = PsiUtilCore.getElementType(leafAtStart);
    if (startLeafType instanceof IReparseableLeafElementType &&
        startLeafRange.getEndOffset() == changedPsiRange.getEndOffset() &&
        (!isTemplateFile || startLeafType instanceof OuterLanguageElementType)) {
      Couple<ASTNode> reparseResult = reparser.reparseNode(leafAtStart);
      if (reparseResult != null && reparseResult.first != null) {
        return reparseResult;
      }
    }
    IElementType endLeafType = PsiUtilCore.getElementType(leafAtEnd);
    if (endLeafType instanceof IReparseableLeafElementType &&
        endLeafRange.getStartOffset() == changedPsiRange.getStartOffset() &&
        (!isTemplateFile || endLeafType instanceof OuterLanguageElementType)) {
      Couple<ASTNode> reparseResult = reparser.reparseNode(leafAtEnd);
      if (reparseResult != null && reparseResult.first != null) {
        return reparseResult;
      }
    }

    while (node != null && !(node instanceof FileElement)) {
      if (isTemplateFile && !(PsiUtilCore.getElementType(node) instanceof OuterLanguageElementType)) {
        return null;
      }
      Couple<ASTNode> couple = reparser.reparseNode(node);
      if (couple != null) {
        if (couple.first == null) {
          return null;
        }
        return couple;
      }
      node = node.getTreeParent();
    }
    return null;
  }

  private static @Nullable ASTNode tryReparseNode(@NotNull IReparseableElementTypeBase reparseable,
                                                  @NotNull ASTNode node,
                                                  @NotNull CharSequence newTextStr,
                                                  @NotNull PsiManager manager,
                                                  @NotNull Language baseLanguage,
                                                  @NotNull CharTable charTable) {
    if (!reparseable.isReparseable(node, newTextStr, baseLanguage, manager.getProject())) {
      return null;
    }
    ASTNode chameleon;
    if (reparseable instanceof ICustomParsingType) {
      chameleon = ((ICustomParsingType)reparseable).parse(newTextStr, SharedImplUtil.findCharTableByTree(node));
    }
    else if (reparseable instanceof ILazyParseableElementType) {
      chameleon = ((ILazyParseableElementType)reparseable).createNode(newTextStr);
    }
    else {
      throw new AssertionError(reparseable.getClass() + " must either implement ICustomParsingType or extend ILazyParseableElementType");
    }
    if (chameleon == null) {
      return null;
    }
    DummyHolder holder = DummyHolderFactory.createHolder(manager, null, node.getPsi(), charTable);
    holder.getTreeElement().rawAddChildren((TreeElement)chameleon);
    if (!reparseable.isValidReparse(node, chameleon)) {
      return null;
    }
    return chameleon;
  }

  @SuppressWarnings("unchecked")
  private static @Nullable ASTNode tryReparseLeaf(@NotNull IReparseableLeafElementType reparseable,
                                                  @NotNull ASTNode node,
                                                  @NotNull CharSequence newTextStr) {
    return reparseable.reparseLeaf(node, newTextStr);
  }

  private static void reportInconsistentLength(PsiFile file, CharSequence newFileText, ASTNode node, int start, int end) {
    @NonNls String message = "Index out of bounds: type=" + node.getElementType() +
                             "; file=" + file +
                             "; file.class=" + file.getClass() +
                             "; start=" + start +
                             "; end=" + end +
                             "; length=" + node.getTextLength();
    String newTextBefore = newFileText.subSequence(0, start).toString();
    String oldTextBefore = file.getText().subSequence(0, start).toString();
    if (oldTextBefore.equals(newTextBefore)) {
      message += "; oldTextBefore==newTextBefore";
    }
    LOG.error(message,
              new Attachment(file.getName() + "_oldNodeText.txt", node.getText()),
              new Attachment(file.getName() + "_oldFileText.txt", file.getText()),
              new Attachment(file.getName() + "_newFileText.txt", newFileText.toString())
    );
  }

  // returns diff log, new file element
  static @NotNull ReparseResult makeFullParse(@NotNull PsiFileImpl fileImpl,
                                              @NotNull FileASTNode oldFileNode,
                                              @NotNull CharSequence newFileText,
                                              @NotNull ProgressIndicator indicator,
                                              @NotNull CharSequence lastCommittedText) {
    if (fileImpl instanceof PsiCodeFragment) {
      FileElement parent = fileImpl.getTreeElement();
      PsiElement context = fileImpl.getContext();
      DummyHolder dummyHolder = new DummyHolder(fileImpl.getManager(), context != null && context.isValid() ? context : null);
      FileElement holderElement = dummyHolder.getTreeElement();
      holderElement.rawAddChildren(fileImpl.createContentLeafElement(holderElement.getCharTable().intern(newFileText, 0, newFileText.length())));
      DiffLog diffLog = new DiffLog();
      diffLog.appendReplaceFileElement(parent, (FileElement)holderElement.getFirstChildNode());

      return new ReparseResult(diffLog, oldFileNode, holderElement);
    }
    FileViewProvider viewProvider = fileImpl.getViewProvider();
    viewProvider.getLanguages();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    FileType fileType = virtualFile.getFileType();
    String fileName = fileImpl.getName();
    LightVirtualFile lightFile = new LightVirtualFile(fileName, fileType, newFileText, virtualFile.getCharset(),
                                                      viewProvider.getModificationStamp());
    lightFile.setOriginalFile(virtualFile);

    FileViewProvider providerCopy = viewProvider.createCopy(lightFile);
    if (providerCopy.isEventSystemEnabled()) {
      throw new AssertionError("Copied view provider must be non-physical for reparse to deliver correct events: " + viewProvider);
    }
    providerCopy.getLanguages();
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(lightFile); // optimization: do not convert file contents to bytes to determine if we should codeinsight it
    PsiFileImpl newFile = getFileCopy(fileImpl, providerCopy);

    newFile.setOriginalFile(fileImpl);

    ASTNode newFileElement = newFile.getNode();
    if (lastCommittedText.length() != oldFileNode.getTextLength()) {
      throw new IncorrectOperationException(viewProvider.toString());
    }
    DiffLog diffLog = mergeTrees(fileImpl, oldFileNode, newFileElement, indicator, lastCommittedText);

    return new ReparseResult(diffLog, oldFileNode, newFileElement);
  }

  public static @NotNull PsiFileImpl getFileCopy(@NotNull PsiFileImpl originalFile, @NotNull FileViewProvider providerCopy) {
    FileViewProvider viewProvider = originalFile.getViewProvider();
    Language language = originalFile.getLanguage();

    PsiFile file = providerCopy.getPsi(language);
    if (file != null && !(file instanceof PsiFileImpl)) {
      throw new RuntimeException("View provider " + viewProvider + " refused to provide PsiFileImpl for " + language + details(providerCopy, viewProvider) +" and returned this strange thing instead of PsiFileImpl: "+file +" ("+file.getClass()+")");
    }

    PsiFileImpl newFile = (PsiFileImpl)file;

    if (newFile == null && language == PlainTextLanguage.INSTANCE && originalFile == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
      newFile = (PsiFileImpl)providerCopy.getPsi(providerCopy.getBaseLanguage());
    }

    if (newFile == null) {
      throw new RuntimeException("View provider " + viewProvider + " refused to parse text with " + language + details(providerCopy, viewProvider));
    }

    return newFile;
  }

  private static @NonNls String details(@NotNull FileViewProvider providerCopy, @NotNull FileViewProvider viewProvider) {
    return "; languages: " + viewProvider.getLanguages() +
           "; base: " + viewProvider.getBaseLanguage() +
           "; copy: " + providerCopy +
           "; copy.base: " + providerCopy.getBaseLanguage() +
           "; vFile: " + viewProvider.getVirtualFile() +
           "; copy.vFile: " + providerCopy.getVirtualFile() +
           "; fileType: " + viewProvider.getVirtualFile().getFileType() +
           "; copy.original(): " +
           (VirtualFileUtil.originalFile(providerCopy.getVirtualFile()));
  }

  private static @NotNull DiffLog replaceElementWithEvents(@NotNull ASTNode oldRoot, @NotNull ASTNode newRoot) {
    DiffLog diffLog = new DiffLog();
    if (oldRoot instanceof CompositeElement) {
      diffLog.appendReplaceElementWithEvents((CompositeElement)oldRoot, (CompositeElement)newRoot);
    }
    else {
      diffLog.nodeReplaced(oldRoot, newRoot);
    }
    return diffLog;
  }

  public static @NotNull DiffLog mergeTrees(@NotNull PsiFileImpl fileImpl,
                                            @NotNull ASTNode oldRoot,
                                            @NotNull ASTNode newRoot,
                                            @NotNull ProgressIndicator indicator,
                                            @NotNull CharSequence lastCommittedText) {
    PsiUtilCore.ensureValid(fileImpl);
    if (newRoot instanceof FileElement) {
      FileElement fileImplElement = fileImpl.getTreeElement();
      if (fileImplElement != null) {
        ((FileElement)newRoot).setCharTable(fileImplElement.getCharTable());
      }
    }

    try {
      newRoot.putUserData(TREE_TO_BE_REPARSED, Pair.create(oldRoot, lastCommittedText));
      if (isReplaceWholeNode(fileImpl, newRoot)) {  // maybe reparsed exception can be thrown here
        DiffLog treeChangeEvent = replaceElementWithEvents(oldRoot, newRoot);
        fileImpl.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);

        return treeChangeEvent;
      }
      newRoot.getFirstChildNode();  // maybe reparsed in PsiBuilderImpl and have thrown exception here
    }
    catch (ReparsedSuccessfullyException e) {
      // reparsed in PsiBuilderImpl
      return e.getDiffLog();
    }
    finally {
      newRoot.putUserData(TREE_TO_BE_REPARSED, null);
    }

    List<CustomLanguageASTComparator> customLanguageASTComparators = CustomLanguageASTComparator.getMatchingComparators(fileImpl);
    ASTShallowComparator comparator = new ASTShallowComparator(indicator, customLanguageASTComparators);
    ASTStructure treeStructure = createInterruptibleASTStructure(newRoot, indicator);

    DiffLog diffLog = new DiffLog();
    diffTrees(oldRoot, diffLog, comparator, treeStructure, indicator, lastCommittedText);
    return diffLog;
  }

  public static <T> void diffTrees(@NotNull ASTNode oldRoot,
                                   @NotNull DiffTreeChangeBuilder<ASTNode, T> builder,
                                   @NotNull ShallowNodeComparator<ASTNode, T> comparator,
                                   @NotNull FlyweightCapableTreeStructure<T> newTreeStructure,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull CharSequence lastCommittedText) {
    DiffTree.diff(createInterruptibleASTStructure(oldRoot, indicator), newTreeStructure, comparator, builder, lastCommittedText);
  }

  private static @NotNull ASTStructure createInterruptibleASTStructure(@NotNull ASTNode oldRoot, @NotNull ProgressIndicator indicator) {
    return new ASTStructure(oldRoot) {
      @Override
      public int getChildren(@NotNull ASTNode astNode, @NotNull Ref<ASTNode[]> into) {
        indicator.checkCanceled();
        return super.getChildren(astNode, into);
      }
    };
  }

  private static boolean isReplaceWholeNode(@NotNull PsiFileImpl fileImpl, @NotNull ASTNode newRoot) throws ReparsedSuccessfullyException {
    Boolean data = fileImpl.getUserData(DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) fileImpl.putUserData(DO_NOT_REPARSE_INCREMENTALLY, null);

    boolean explicitlyMarkedDeep = Boolean.TRUE.equals(data);

    if (explicitlyMarkedDeep || isTooDeep(fileImpl)) {
      return true;
    }

    ASTNode childNode = newRoot.getFirstChildNode();  // maybe reparsed in PsiBuilderImpl and have thrown exception here
    boolean childTooDeep = isTooDeep(childNode);
    if (childTooDeep) {
      childNode.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, null);
      fileImpl.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
    }
    return childTooDeep;
  }

  private static final class Reparser {
    private final @NotNull PsiFileImpl myFile;
    private final CharTable myCharTable;
    private final Language myBaseLanguage;

    private final @NotNull CharSequence myNewFileText;
    private final int myLengthShift;

    Reparser(@NotNull PsiFileImpl file, @NotNull FileASTNode oldFileNode, @NotNull CharSequence newFileText) {
      myLengthShift = newFileText.length() - oldFileNode.getTextLength();
      myBaseLanguage = file.getViewProvider().getBaseLanguage();
      myNewFileText = newFileText;
      myFile = file;
      myCharTable = oldFileNode.getCharTable();
    }

    /**
     * @return {@code null} means "try reparsing parent"
     *         {@code Pair(null, null)} means "reparsing failed, stop trying to reparse"
     *         {@code Pair(!null, !null)} means "reparsing succeeded"
     */
    @Nullable Couple<ASTNode> reparseNode(@NotNull ASTNode node) {
      IElementType elementType = node.getElementType();
      if (!(elementType instanceof IReparseableElementTypeBase || elementType instanceof IReparseableLeafElementType)) {
        return null;
      }

      TextRange textRange = node.getTextRange();

      if (textRange.getLength() + myLengthShift <= 0 ||
          !myBaseLanguage.isKindOf(elementType.getLanguage()) &&
          !(elementType instanceof IReparseableLeafElementType) &&
          TreeUtil.containsOuterLanguageElements(node)) {
        return null;
      }

      int start = textRange.getStartOffset();
      int end = start + textRange.getLength() + myLengthShift;
      if (end > myNewFileText.length()) {
        reportInconsistentLength(myFile, myNewFileText, node, start, end);
        return Couple.of(null, null);
      }

      CharSequence newTextStr = myNewFileText.subSequence(start, end);

      ASTNode newNode;
      if (elementType instanceof IReparseableElementTypeBase) {
        newNode =
          tryReparseNode((IReparseableElementTypeBase)elementType, node, newTextStr, myFile.getManager(), myBaseLanguage, myCharTable);
      }
      else {
        assert elementType instanceof IReparseableLeafElementType;
        newNode = tryReparseLeaf((IReparseableLeafElementType<?>)elementType, node, newTextStr);
      }

      if (newNode == null) {
        return null;
      }

      if (newNode.getTextLength() != newTextStr.length()) {
        String details = ApplicationManager.getApplication().isInternal()
                         ? "text=" + newTextStr + "; treeText=" + newNode.getText() + ";"
                         : "";
        LOG.error("Inconsistent reparse: " + details + " type=" + elementType);
      }

      return Couple.of(node, newNode);
    }
  }
}

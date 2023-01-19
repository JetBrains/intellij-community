// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class BlockSupportImpl extends BlockSupport {
  private static final Logger LOG = Logger.getInstance(BlockSupportImpl.class);

  @Override
  public void reparseRange(@NotNull PsiFile file, int startOffset, int endOffset, @NotNull CharSequence newText)
    throws IncorrectOperationException {
    LOG.assertTrue(file.isValid());
    PsiFileImpl psiFile = (PsiFileImpl)file;
    Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
    document.replaceString(startOffset, endOffset, newText);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
  }

  @Override
  @NotNull
  public DiffLog reparseRange(@NotNull PsiFile file,
                              @NotNull FileASTNode oldFileNode,
                              @NotNull TextRange changedPsiRange,
                              @NotNull CharSequence newFileText,
                              @NotNull ProgressIndicator indicator,
                              @NotNull CharSequence lastCommittedText) {
    return reparse(file, oldFileNode, changedPsiRange, newFileText, indicator, lastCommittedText).log;
  }

  static class ReparseResult {
    final DiffLog log;
    final ASTNode oldRoot;
    final ASTNode newRoot;

    ReparseResult(DiffLog log, ASTNode oldRoot, ASTNode newRoot) {
      this.log = log;
      this.oldRoot = oldRoot;
      this.newRoot = newRoot;
    }

  }
  // return diff log, old node to replace, new node (in dummy file)
  // MUST call .close() on the returned result
  @NotNull
  static ReparseResult reparse(@NotNull PsiFile file,
                               @NotNull FileASTNode oldFileNode,
                               @NotNull TextRange changedPsiRange,
                               @NotNull CharSequence newFileText,
                               @NotNull ProgressIndicator indicator,
                               @NotNull CharSequence lastCommittedText) {
    PsiFileImpl fileImpl = (PsiFileImpl)file;

    Couple<ASTNode> reparseableRoots = findReparseableRoots(fileImpl, oldFileNode, changedPsiRange, newFileText);
    if (reparseableRoots == null) {
      return makeFullParse(fileImpl, oldFileNode, newFileText, indicator, lastCommittedText);
    }
    ASTNode oldRoot = reparseableRoots.first;
    ASTNode newRoot = reparseableRoots.second;
    DiffLog diffLog = mergeTrees(fileImpl, oldRoot, newRoot, indicator, lastCommittedText);
    return new ReparseResult(diffLog, oldRoot, newRoot);
  }


  /**
   * Find ast node that could be reparsed incrementally
   * @return Pair (target reparseable node, new replacement node)
   *         or {@code null} if can't parse incrementally.
   */
  @Nullable
  public static Couple<ASTNode> findReparseableRoots(@NotNull PsiFileImpl file,
                                                     @NotNull FileASTNode oldFileNode,
                                                     @NotNull TextRange changedPsiRange,
                                                     @NotNull CharSequence newFileText) {
    CharTable charTable = oldFileNode.getCharTable();
    int lengthShift = newFileText.length() - oldFileNode.getTextLength();

    if (isTooDeep(file)) {
      return null;
    }

    boolean isTemplateFile = oldFileNode.getElementType() instanceof ITemplateDataElementType;

    ASTNode leafAtStart = oldFileNode.findLeafElementAt(Math.max(0, changedPsiRange.getStartOffset() - 1));
    ASTNode leafAtEnd = oldFileNode.findLeafElementAt(Math.min(changedPsiRange.getEndOffset(), oldFileNode.getTextLength() - 1));
    ASTNode node = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : oldFileNode;
    Language baseLanguage = file.getViewProvider().getBaseLanguage();

    Function<ASTNode, Couple<ASTNode>> reparseNodeFunction = astNode -> {
      IElementType elementType = astNode.getElementType();
      if (elementType instanceof IReparseableElementTypeBase || elementType instanceof IReparseableLeafElementType) {
        TextRange textRange = astNode.getTextRange();

        if (textRange.getLength() + lengthShift > 0 &&
            (baseLanguage.isKindOf(elementType.getLanguage()) || elementType instanceof IReparseableLeafElementType ||
             !TreeUtil.containsOuterLanguageElements(astNode))) {
          int start = textRange.getStartOffset();
          int end = start + textRange.getLength() + lengthShift;
          if (end > newFileText.length()) {
            reportInconsistentLength(file, newFileText, astNode, start, end);
            return Couple.of(null, null);
          }

          CharSequence newTextStr = newFileText.subSequence(start, end);

          ASTNode newNode;
          if (elementType instanceof IReparseableElementTypeBase) {
            newNode =
              tryReparseNode((IReparseableElementTypeBase)elementType, astNode, newTextStr, file.getManager(), baseLanguage, charTable);
          }
          else {
            newNode = tryReparseLeaf((IReparseableLeafElementType)elementType, astNode, newTextStr);
          }

          if (newNode != null) {
            if (newNode.getTextLength() != newTextStr.length()) {
              String details = ApplicationManager.getApplication().isInternal()
                               ? "text=" + newTextStr + "; treeText=" + newNode.getText() + ";"
                               : "";
              LOG.error("Inconsistent reparse: " + details + " type=" + elementType);
            }

            return Couple.of(astNode, newNode);
          }
        }
      }
      return null;
    };

    TextRange startLeafRange = leafAtStart == null ? null : leafAtStart.getTextRange();
    TextRange endLeafRange = leafAtEnd == null ? null : leafAtEnd.getTextRange();

    IElementType startLeafType = PsiUtilCore.getElementType(leafAtStart);
    if (startLeafType instanceof IReparseableLeafElementType &&
        startLeafRange.getEndOffset() == changedPsiRange.getEndOffset() &&
        (!isTemplateFile || startLeafType instanceof OuterLanguageElementType)) {
      Couple<ASTNode> reparseResult = reparseNodeFunction.apply(leafAtStart);
      if (reparseResult != null && reparseResult.first != null) {
        return reparseResult;
      }
    }
    IElementType endLeafType = PsiUtilCore.getElementType(leafAtEnd);
    if (endLeafType instanceof IReparseableLeafElementType &&
        endLeafRange.getStartOffset() == changedPsiRange.getStartOffset() &&
        (!isTemplateFile || endLeafType instanceof OuterLanguageElementType)) {
      Couple<ASTNode> reparseResult = reparseNodeFunction.apply(leafAtEnd);
      if (reparseResult != null && reparseResult.first != null) {
        return reparseResult;
      }
    }

    while (node != null && !(node instanceof FileElement)) {
      if (isTemplateFile && !(PsiUtilCore.getElementType(node) instanceof OuterLanguageElementType)) {
        break;
      }
      Couple<ASTNode> couple = reparseNodeFunction.apply(node);
      if (couple != null) {
        if (couple.first == null) {
          break;
        }
        return couple;
      }
      node = node.getTreeParent();
    }
    return null;
  }

  @Nullable
  protected static ASTNode tryReparseNode(@NotNull IReparseableElementTypeBase reparseable, @NotNull ASTNode node, @NotNull CharSequence newTextStr,
                                          @NotNull PsiManager manager, @NotNull Language baseLanguage, @NotNull CharTable charTable) {
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

  @Nullable
  @SuppressWarnings("unchecked")
  protected static ASTNode tryReparseLeaf(@NotNull IReparseableLeafElementType reparseable, @NotNull ASTNode node, @NotNull CharSequence newTextStr) {
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
  @NotNull
  static ReparseResult makeFullParse(@NotNull PsiFileImpl fileImpl,
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

  @NotNull
  public static PsiFileImpl getFileCopy(@NotNull PsiFileImpl originalFile, @NotNull FileViewProvider providerCopy) {
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

  private static @NonNls String details(FileViewProvider providerCopy, FileViewProvider viewProvider) {
    return "; languages: " + viewProvider.getLanguages() +
           "; base: " + viewProvider.getBaseLanguage() +
           "; copy: " + providerCopy +
           "; copy.base: " + providerCopy.getBaseLanguage() +
           "; vFile: " + viewProvider.getVirtualFile() +
           "; copy.vFile: " + providerCopy.getVirtualFile() +
           "; fileType: " + viewProvider.getVirtualFile().getFileType() +
           "; copy.original(): " +
           (providerCopy.getVirtualFile() instanceof LightVirtualFile ? ((LightVirtualFile)providerCopy.getVirtualFile()).getOriginalFile() : null);
  }

  @NotNull
  private static DiffLog replaceElementWithEvents(@NotNull ASTNode oldRoot, @NotNull ASTNode newRoot) {
    DiffLog diffLog = new DiffLog();
    if (oldRoot instanceof CompositeElement) {
      diffLog.appendReplaceElementWithEvents((CompositeElement)oldRoot, (CompositeElement)newRoot);
    }
    else {
      diffLog.nodeReplaced(oldRoot, newRoot);
    }
    return diffLog;
  }

  @NotNull
  public static DiffLog mergeTrees(@NotNull PsiFileImpl fileImpl,
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
      if (isReplaceWholeNode(fileImpl, newRoot)) {
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

    ASTShallowComparator comparator = new ASTShallowComparator(indicator);
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

  private static ASTStructure createInterruptibleASTStructure(@NotNull ASTNode oldRoot, @NotNull ProgressIndicator indicator) {
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
}

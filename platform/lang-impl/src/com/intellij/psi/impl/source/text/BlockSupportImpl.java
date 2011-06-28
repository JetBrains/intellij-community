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

package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.templateLanguages.ITemplateDataElementType;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockSupportImpl extends BlockSupport {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.text.BlockSupportImpl");

  public BlockSupportImpl(Project project) {
    project.getMessageBus().connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      public void updateStarted(final Document doc) {
        doc.putUserData(DO_NOT_REPARSE_INCREMENTALLY,  Boolean.TRUE);
      }
    });
  }

  public void reparseRange(PsiFile file, int startOffset, int endOffset, CharSequence newTextS) throws IncorrectOperationException {
    LOG.assertTrue(file.isValid());
    final PsiFileImpl psiFile = (PsiFileImpl)file;
    final Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
    document.replaceString(startOffset, endOffset, newTextS);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
  }

  @NotNull
  public DiffLog reparseRange(@NotNull final PsiFile file,
                              final int startOffset,
                              final int endOffset,
                              final int lengthShift,
                              @NotNull final CharSequence newFileText,
                              @NotNull final ProgressIndicator indicator) {
    return reparseRangeInternal(file, startOffset > 0 ? startOffset - 1 : 0, endOffset, lengthShift, newFileText, indicator);
  }

  @NotNull
  private static DiffLog reparseRangeInternal(@NotNull PsiFile file,
                                              int startOffset,
                                              int endOffset,
                                              int lengthShift,
                                              @NotNull CharSequence newFileText,
                                              @NotNull ProgressIndicator indicator) {
    file.getViewProvider().beforeContentsSynchronized();
    final PsiFileImpl fileImpl = (PsiFileImpl)file;
    Project project = fileImpl.getProject();
    final FileElement treeFileElement = fileImpl.getTreeElement();
    final CharTable charTable = treeFileElement.getCharTable();

    final int textLength = treeFileElement.getTextLength() + lengthShift;

    if (treeFileElement.getElementType() instanceof ITemplateDataElementType || isTooDeep(file)) {
      // unable to perform incremental reparse for template data in JSP, or in exceptionally deep trees
      return makeFullParse(treeFileElement, newFileText, textLength, fileImpl, indicator);
    }

    final ASTNode leafAtStart = treeFileElement.findLeafElementAt(startOffset);
    final ASTNode leafAtEnd = treeFileElement.findLeafElementAt(endOffset);
    ASTNode node = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : treeFileElement;
    Language baseLanguage = file.getViewProvider().getBaseLanguage();

    while (node != null && !(node instanceof FileElement)) {
      IElementType elementType = node.getElementType();
      if (elementType instanceof IReparseableElementType) {
        final TextRange textRange = node.getTextRange();
        final IReparseableElementType reparseable = (IReparseableElementType)elementType;

        if (reparseable.getLanguage() == baseLanguage) {
          final int start = textRange.getStartOffset();
          final int end = start + textRange.getLength() + lengthShift;
          assertFileLength(file, newFileText, node, elementType, start, end);

          CharSequence newTextStr = newFileText.subSequence(start, end);

          if (reparseable.isParsable(newTextStr, project)) {
            ASTNode chameleon = reparseable.createNode(newTextStr);
            if (chameleon != null) {
              DummyHolder holder = DummyHolderFactory.createHolder(fileImpl.getManager(), null, node.getPsi(), charTable);
              holder.getTreeElement().rawAddChildren((TreeElement)chameleon);

              if (holder.getTextLength() != newTextStr.length()) {
                String details = ApplicationManagerEx.getApplicationEx().isInternal()
                           ? "text=" + newTextStr + "; treeText=" + holder.getText() + ";"
                           : "";
                LOG.error("Inconsistent reparse: " + details + " type=" + elementType);
              }

              return mergeTrees(fileImpl, node, chameleon, indicator);
            }
          }
        }
      }
      node = node.getTreeParent();
    }

    return makeFullParse(node, newFileText, textLength, fileImpl, indicator);
  }

  private static void assertFileLength(PsiFile file, CharSequence newFileText, ASTNode node, IElementType elementType, int start, int end) {
    if (end > newFileText.length() && ApplicationManagerEx.getApplicationEx().isInternal()) {
      String newTextBefore = newFileText.subSequence(0, start).toString();
      String oldTextBefore = file.getText().subSequence(0, start).toString();
      String message = "IOOBE: type=" + elementType +
                       "; oldText=" + node.getText() +
                       "; newText=" + newFileText.subSequence(start, newFileText.length()) +
                       "; length=" + node.getTextLength();
      if (oldTextBefore.equals(newTextBefore)) {
        message += "; oldTextBefore==newTextBefore";
      } else {
        message += "; oldTextBefore=" + oldTextBefore +
                   "; newTextBefore=" + newTextBefore;
      }
      throw new AssertionError(message);
    }
  }

  @NotNull
  private static DiffLog makeFullParse(ASTNode parent,
                                       @NotNull CharSequence newFileText,
                                       int textLength,
                                       @NotNull PsiFileImpl fileImpl,
                                       @NotNull ProgressIndicator indicator) {
    if (fileImpl instanceof PsiCodeFragment) {
      final FileElement holderElement = new DummyHolder(fileImpl.getManager(), null).getTreeElement();
      holderElement.rawAddChildren(fileImpl.createContentLeafElement(holderElement.getCharTable().intern(newFileText, 0, textLength)));
      DiffLog diffLog = new DiffLog();
      diffLog.appendReplaceFileElement((FileElement)parent, (FileElement)holderElement.getFirstChildNode());

      return diffLog;
    }
    else {
      FileViewProvider viewProvider = fileImpl.getViewProvider();
      viewProvider.getLanguages();
      FileType fileType = viewProvider.getVirtualFile().getFileType();
      final LightVirtualFile lightFile = new LightVirtualFile(fileImpl.getName(), fileType, newFileText, viewProvider.getVirtualFile().getCharset(),
                                                              fileImpl.getModificationStamp());
      lightFile.setOriginalFile(viewProvider.getVirtualFile());

      FileViewProvider copy = viewProvider.createCopy(lightFile);
      copy.getLanguages();
      Language language = fileImpl.getLanguage();
      SingleRootFileViewProvider.doNotCheckFileSizeLimit(lightFile); // optimization: do not convert file contents to bytes to determine if we should codeinsight it
      final PsiFileImpl newFile = (PsiFileImpl)copy.getPsi(language);

      if (newFile == null) {
        LOG.error("View provider " + viewProvider + " refused to parse text with " + language +
                  "; base: " + viewProvider.getBaseLanguage() + "; copy: " + copy.getBaseLanguage() + "; fileType: " + fileType);
        return null;
      }

      newFile.setOriginalFile(fileImpl);

      final FileElement newFileElement = (FileElement)newFile.getNode();
      final FileElement oldFileElement = (FileElement)fileImpl.getNode();
                                                            
      assert oldFileElement != null && newFileElement != null;
      DiffLog diffLog = mergeTrees(fileImpl, oldFileElement, newFileElement, indicator);

      ((PsiManagerEx)fileImpl.getManager()).getFileManager().setViewProvider(lightFile, null);
      return diffLog;
    }
  }

  @NotNull
  private static DiffLog replaceElementWithEvents(final CompositeElement oldRoot,
                                                  final CompositeElement newRoot) {
    DiffLog diffLog = new DiffLog();
    diffLog.appendReplaceElementWithEvents(oldRoot, newRoot);
    return diffLog;
  }

  @NotNull
  public static DiffLog mergeTrees(@NotNull final PsiFileImpl fileImpl,
                                   @NotNull final ASTNode oldRoot,
                                   @NotNull final ASTNode newRoot,
                                   @NotNull ProgressIndicator indicator) {
    if (newRoot instanceof FileElement) {
      ((FileElement)newRoot).setCharTable(fileImpl.getTreeElement().getCharTable());
    }

    try {
      if (isReplaceWholeNode(fileImpl, newRoot)) {
        DiffLog treeChangeEvent = replaceElementWithEvents((CompositeElement)oldRoot, (CompositeElement)newRoot);
        fileImpl.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
        return treeChangeEvent;
      }
      newRoot.putUserData(TREE_TO_BE_REPARSED, oldRoot);
      newRoot.getFirstChildNode();  // maybe reparsed in PsiBuilderImpl and have thrown exception here
    }
    catch (ReparsedSuccessfullyException e) {
      // reparsed in PsiBuilderImpl
      return e.getDiffLog();
    }

    final ASTShallowComparator comparator = new ASTShallowComparator(indicator);
    final ASTStructure treeStructure = createInterruptibleASTStructure(newRoot, indicator);

    DiffLog diffLog = new DiffLog();
    diffTrees(oldRoot, diffLog, comparator, treeStructure, indicator);
    return diffLog;
  }

  public static <T> void diffTrees(@NotNull final ASTNode oldRoot,
                                   @NotNull final DiffTreeChangeBuilder<ASTNode, T> builder,
                                   @NotNull final ShallowNodeComparator<ASTNode, T> comparator,
                                   @NotNull final FlyweightCapableTreeStructure<T> newTreeStructure,
                                   final ProgressIndicator indicator) {
    TreeUtil.ensureParsedRecursivelyCheckingProgress(oldRoot, indicator);
    DiffTree.diff(createInterruptibleASTStructure(oldRoot, indicator), newTreeStructure, comparator, builder);
  }

  private static ASTStructure createInterruptibleASTStructure(@NotNull final ASTNode oldRoot, @Nullable final ProgressIndicator indicator) {
    return new ASTStructure(oldRoot) {
      @Override
      public int getChildren(@NotNull ASTNode astNode, @NotNull Ref<ASTNode[]> into) {
        if (indicator != null) {
          indicator.checkCanceled();
        }
        return super.getChildren(astNode, into);
      }
    };
  }

  private static boolean isReplaceWholeNode(@NotNull PsiFileImpl fileImpl, @NotNull ASTNode newRoot) throws ReparsedSuccessfullyException{
    final Boolean data = fileImpl.getUserData(DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) fileImpl.putUserData(DO_NOT_REPARSE_INCREMENTALLY, null);

    boolean explicitlyMarkedDeep = Boolean.TRUE.equals(data);

    if (explicitlyMarkedDeep || isTooDeep(fileImpl)) {
      return true;
    }

    final ASTNode childNode = newRoot.getFirstChildNode();  // maybe reparsed in PsiBuilderImpl and have thrown exception here
    boolean childTooDeep = isTooDeep(childNode);
    if (childTooDeep) {
      childNode.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, null);
      fileImpl.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
    }
    return childTooDeep;
  }

  public static void sendBeforeChildrenChangeEvent(final PsiElement scope) {
    if(!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope.getContainingFile());
    event.setOffset(scope.getTextRange().getStartOffset());
    event.setOldLength(scope.getTextLength());
    manager.beforeChildrenChange(event);
  }

  public static void sendAfterChildrenChangedEvent(final PsiFileImpl scope, int oldLength) {
    if (!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(oldLength);
    manager.childrenChanged(event);
  }
}

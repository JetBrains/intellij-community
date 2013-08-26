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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
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
      @Override
      public void updateStarted(@NotNull final Document doc) {
        doc.putUserData(DO_NOT_REPARSE_INCREMENTALLY,  Boolean.TRUE);
      }
    });
  }

  @Override
  public void reparseRange(PsiFile file, int startOffset, int endOffset, CharSequence newTextS) throws IncorrectOperationException {
    LOG.assertTrue(file.isValid());
    final PsiFileImpl psiFile = (PsiFileImpl)file;
    final Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
    document.replaceString(startOffset, endOffset, newTextS);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
  }

  @Override
  @NotNull
  public DiffLog reparseRange(@NotNull final PsiFile file,
                              TextRange changedPsiRange,
                              @NotNull final CharSequence newFileText,
                              @NotNull final ProgressIndicator indicator) {
    final PsiFileImpl fileImpl = (PsiFileImpl)file;
    Project project = fileImpl.getProject();
    final FileElement treeFileElement = fileImpl.getTreeElement();
    final CharTable charTable = treeFileElement.getCharTable();


    final int textLength = newFileText.length();
    int lengthShift = textLength - treeFileElement.getTextLength();

    if (treeFileElement.getElementType() instanceof ITemplateDataElementType || isTooDeep(file)) {
      // unable to perform incremental reparse for template data in JSP, or in exceptionally deep trees
      return makeFullParse(treeFileElement, newFileText, textLength, fileImpl, indicator);
    }

    final ASTNode leafAtStart = treeFileElement.findLeafElementAt(Math.max(0, changedPsiRange.getStartOffset() - 1));
    final ASTNode leafAtEnd = treeFileElement.findLeafElementAt(changedPsiRange.getEndOffset());
    ASTNode node = leafAtStart != null && leafAtEnd != null ? TreeUtil.findCommonParent(leafAtStart, leafAtEnd) : treeFileElement;
    Language baseLanguage = file.getViewProvider().getBaseLanguage();

    while (node != null && !(node instanceof FileElement)) {
      IElementType elementType = node.getElementType();
      if (elementType instanceof IReparseableElementType) {
        final TextRange textRange = node.getTextRange();
        final IReparseableElementType reparseable = (IReparseableElementType)elementType;

        if (baseLanguage.isKindOf(reparseable.getLanguage())) {
          final int start = textRange.getStartOffset();
          final int end = start + textRange.getLength() + lengthShift;
          assertFileLength(file, newFileText, node, elementType, start, end);

          CharSequence newTextStr = newFileText.subSequence(start, end);

          if (reparseable.isParsable(newTextStr, baseLanguage, project)) {
            ASTNode chameleon = reparseable.createNode(newTextStr);
            if (chameleon != null) {
              DummyHolder holder = DummyHolderFactory.createHolder(fileImpl.getManager(), null, node.getPsi(), charTable);
              holder.getTreeElement().rawAddChildren((TreeElement)chameleon);

              if (holder.getTextLength() != newTextStr.length()) {
                String details = ApplicationManager.getApplication().isInternal()
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
    if (end > newFileText.length() && ApplicationManager.getApplication().isInternal()) {
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
      String fileName = fileImpl.getName();
      final LightVirtualFile lightFile = new LightVirtualFile(fileName, fileType, newFileText, viewProvider.getVirtualFile().getCharset(),
                                                              fileImpl.getViewProvider().getModificationStamp());
      lightFile.setOriginalFile(viewProvider.getVirtualFile());

      FileViewProvider copy = viewProvider.createCopy(lightFile);
      copy.getLanguages();
      SingleRootFileViewProvider.doNotCheckFileSizeLimit(lightFile); // optimization: do not convert file contents to bytes to determine if we should codeinsight it
      PsiFileImpl newFile = getFileCopy(fileImpl, copy);

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
  public static PsiFileImpl getFileCopy(PsiFileImpl originalFile, FileViewProvider providerCopy) {
    FileViewProvider viewProvider = originalFile.getViewProvider();
    Language language = originalFile.getLanguage();
    PsiFileImpl newFile = (PsiFileImpl)providerCopy.getPsi(language);

    if (newFile == null && language == PlainTextLanguage.INSTANCE && originalFile == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
      newFile = (PsiFileImpl)providerCopy.getPsi(providerCopy.getBaseLanguage());
    }

    if (newFile == null) {
      throw new RuntimeException("View provider " + viewProvider + " refused to parse text with " + language +
                                 "; languages: " + viewProvider.getLanguages() +
                                 "; base: " + viewProvider.getBaseLanguage() +
                                 "; copy: " + providerCopy +
                                 "; copy.base: " + providerCopy.getBaseLanguage() +
                                 "; vFile: " + viewProvider.getVirtualFile() +
                                 "; copy.vFile: " + providerCopy.getVirtualFile() +
                                 "; fileType: " + viewProvider.getVirtualFile().getFileType() +
                                 "; copy.original(): " +
                                 (providerCopy.getVirtualFile() instanceof LightVirtualFile ? ((LightVirtualFile)providerCopy.getVirtualFile()).getOriginalFile() : null));
    }

    return newFile;
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
      newRoot.putUserData(TREE_TO_BE_REPARSED, oldRoot);
      if (isReplaceWholeNode(fileImpl, newRoot)) {
        DiffLog treeChangeEvent = replaceElementWithEvents((CompositeElement)oldRoot, (CompositeElement)newRoot);
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

  public static void sendBeforeChildrenChangeEvent(@NotNull PsiManagerImpl manager, @NotNull PsiElement scope, boolean isGenericChange) {
    if(!scope.isPhysical()) {
      manager.beforeChange(false);
      return;
    }
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope.getContainingFile());
    event.setOffset(scope.getTextRange().getStartOffset());
    event.setOldLength(scope.getTextLength());
      // the "generic" event is being sent on every PSI change. It does not carry any specific info except the fact that "something has changed"
    event.setGenericChange(isGenericChange);
    manager.beforeChildrenChange(event);
  }

  public static void sendAfterChildrenChangedEvent(@NotNull PsiManagerImpl manager,
                                                   @NotNull PsiFileImpl scope,
                                                   int oldLength,
                                                   boolean isGenericChange) {
    if(!scope.isPhysical()) {
      manager.afterChange(false);
      return;
    }
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(oldLength);
    event.setGenericChange(isGenericChange);
    manager.childrenChanged(event);
  }
}

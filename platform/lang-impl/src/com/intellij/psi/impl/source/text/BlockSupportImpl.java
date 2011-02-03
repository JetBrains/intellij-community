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
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
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
import org.jetbrains.annotations.NotNull;

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

  public void reparseRange(final PsiFile file,
                           final int startOffset,
                           final int endOffset,
                           final int lengthShift,
                           final CharSequence newFileText) {
    // adjust editor offsets to damage area markers
    file.getManager().performActionWithFormatterDisabled(new Runnable() {
      public void run() {
        reparseRangeInternal(file, startOffset > 0 ? startOffset - 1 : 0, endOffset, lengthShift, newFileText);
      }
    });
  }

  private static void reparseRangeInternal(PsiFile file, int startOffset, int endOffset, int lengthShift, CharSequence newFileText) {
    file.getViewProvider().beforeContentsSynchronized();
    final PsiFileImpl fileImpl = (PsiFileImpl)file;
    Project project = fileImpl.getProject();
    final CharTable charTable = fileImpl.getTreeElement().getCharTable();
    // hack
    final int textLength = file.getTextLength() + lengthShift;

    final FileElement treeFileElement = fileImpl.getTreeElement();

    if (treeFileElement.getElementType() instanceof ITemplateDataElementType || isTooDeep(file)) {
      // unable to perform incremental reparse for template data in JSP, or in exceptionally deep trees
      makeFullParse(treeFileElement, newFileText, textLength, fileImpl);
      return;
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

          CharSequence newTextStr = newFileText.subSequence(start, end);

          if (reparseable.isParsable(newTextStr, project)) {
            ASTNode chameleon = reparseable.createNode(newTextStr);
            if (chameleon != null) {
              DummyHolder holder = DummyHolderFactory.createHolder(fileImpl.getManager(), null, node.getPsi(), charTable);
              holder.getTreeElement().rawAddChildren((TreeElement)chameleon);

              if (holder.getTextLength() != newTextStr.length()) {
                if (ApplicationManagerEx.getApplicationEx().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
                  LOG.error("Inconsistent reparse: text=" + newTextStr + "; treeText=" + holder.getText() + "; type=" + elementType);
                } else {
                  LOG.error("Inconsistent reparse: type=" + elementType);
                }
              }

              mergeTrees(fileImpl, node, chameleon);
              return;
            }
          }
        }
      }
      node = node.getTreeParent();
    }

    makeFullParse(node, newFileText, textLength, fileImpl);
  }

  private static void makeFullParse(final ASTNode parent, final CharSequence newFileText, final int textLength, final PsiFileImpl fileImpl) {
    if (fileImpl instanceof PsiCodeFragment) {
      final FileElement holderElement = new DummyHolder(fileImpl.getManager(), null).getTreeElement();
      holderElement.rawAddChildren(fileImpl.createContentLeafElement(holderElement.getCharTable().intern(newFileText, 0, textLength)));
      replaceFileElement(fileImpl, (FileElement)parent, (FileElement)holderElement.getFirstChildNode(), (PsiManagerEx)fileImpl.getManager());
    }
    else {
      final FileViewProvider viewProvider = fileImpl.getViewProvider();
      FileType fileType = viewProvider.getVirtualFile().getFileType();
      final LightVirtualFile lightFile = new LightVirtualFile(fileImpl.getName(), fileType, newFileText, viewProvider.getVirtualFile().getCharset(),
                                                              fileImpl.getModificationStamp());
      lightFile.setOriginalFile(viewProvider.getVirtualFile());

      final FileViewProvider copy = viewProvider.createCopy(lightFile);
      final PsiFileImpl newFile = (PsiFileImpl)copy.getPsi(fileImpl.getLanguage());

      if (newFile == null) {
        LOG.error("View provider " + viewProvider + " refused to parse text with " + fileImpl.getLanguage() +
                  "; base: " + viewProvider.getBaseLanguage() + "; copy: " + copy.getBaseLanguage() + "; fileType: " + fileType);
        return;
      }

      newFile.setOriginalFile(fileImpl);

      final FileElement newFileElement = (FileElement)newFile.getNode();
      final FileElement oldFileElement = (FileElement)fileImpl.getNode();
                                                            
      final Boolean data = fileImpl.getUserData(DO_NOT_REPARSE_INCREMENTALLY);
      if (data != null) fileImpl.putUserData(DO_NOT_REPARSE_INCREMENTALLY, null);

      if (Boolean.TRUE.equals(data) || isTooDeep(fileImpl)) {
        // TODO: Just to switch off incremental tree patching for certain conditions (like languages) if necessary.
        replaceElementWithEvents(fileImpl, oldFileElement, newFileElement);
      }
      else {
        assert oldFileElement != null && newFileElement != null;
        mergeTrees(fileImpl, oldFileElement, newFileElement);
      }
      ((PsiManagerEx)fileImpl.getManager()).getFileManager().setViewProvider(lightFile, null);
    }
  }

  private static void replaceElementWithEvents(final PsiFileImpl file, final CompositeElement oldRoot, final CompositeElement newRoot) {
    if (newRoot instanceof FileElement) {
      file.getTreeElement().setCharTable(((FileElement)newRoot).getCharTable());
    }
    oldRoot.replaceAllChildrenToChildrenOf(newRoot);
  }

  static void replaceFileElement(final PsiFileImpl fileImpl,
                                 final FileElement fileElement,
                                 final FileElement newFileElement,
                                 final PsiManagerEx manager) {
    final int oldLength = fileElement.getTextLength();
    sendPsiBeforeEvent(fileImpl);
    if (fileElement.getFirstChildNode() != null) fileElement.rawRemoveAllChildren();
    final ASTNode firstChildNode = newFileElement.getFirstChildNode();
    if (firstChildNode != null) fileElement.rawAddChildren((TreeElement)firstChildNode);
    fileImpl.getTreeElement().setCharTable(newFileElement.getCharTable());
    manager.invalidateFile(fileImpl);
    fileElement.subtreeChanged();
    sendPsiAfterEvent(fileImpl, oldLength);
  }

  public static void mergeTrees(@NotNull final PsiFileImpl file, @NotNull final ASTNode oldRoot, @NotNull final ASTNode newRoot) {
    synchronized (PsiLock.LOCK) {
      if (newRoot instanceof FileElement) {
        ((FileElement)newRoot).setCharTable(file.getTreeElement().getCharTable());
      }

      try {
        newRoot.putUserData(TREE_TO_BE_REPARSED, oldRoot);

        final ASTNode childNode;
        try {
          childNode = newRoot.getFirstChildNode(); // Ensure parsed
        }
        catch (ReparsedSuccessfullyException e) {
          return; // Successfully merged in PsiBuilderImpl
        }

        final boolean childTooDeep = isTooDeep(childNode);
        if (isTooDeep(file) || childTooDeep) {
          replaceElementWithEvents(file, (CompositeElement)oldRoot, (CompositeElement)newRoot);

          if (childTooDeep) {
            childNode.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, null);
            file.putUserData(TREE_DEPTH_LIMIT_EXCEEDED, Boolean.TRUE);
          }
          return;
        }

        TreeUtil.ensureParsedRecursively(oldRoot);

        final PomModel model = PomManager.getModel(file.getProject());
        model.runTransaction(new PomTransactionBase(file, model.getModelAspect(TreeAspect.class)) {
          public PomModelEvent runInner() {
            final ASTDiffBuilder builder = new ASTDiffBuilder(file);
            DiffTree.diff(new ASTStructure(oldRoot), new ASTStructure(newRoot), new ASTShallowComparator(), builder);
            file.subtreeChanged();

            return new TreeAspectEvent(model, builder.getEvent());
          }
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      catch (Throwable th) {
        LOG.error(th);
      }
      finally {
        ((PsiManagerEx)file.getManager()).invalidateFile(file);
      }
    }
  }

  private static void sendPsiBeforeEvent(final PsiFile scope) {
    if (!scope.isPhysical()) return;
    final PsiManagerImpl manager = (PsiManagerImpl)scope.getManager();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(scope.getTextLength());
    manager.beforeChildrenChange(event);
  }

  private static void sendPsiAfterEvent(final PsiFileImpl scope, int oldLength) {
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

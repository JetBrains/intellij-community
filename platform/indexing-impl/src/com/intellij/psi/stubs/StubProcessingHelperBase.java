package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Author: dmitrylomov
 */
public abstract class StubProcessingHelperBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubProcessingHelperBase");

  private static IElementType stubType(@NotNull final StubElement<?> stub) {
    if (stub instanceof PsiFileStub) {
      return ((PsiFileStub)stub).getType();
    }

    return stub.getStubType();
  }

  public <Psi extends PsiElement> boolean processStubsInFile(final Project project, final VirtualFile file, StubIdList value, final Processor<? super Psi> processor) {
    StubTree stubTree = null;

    PsiFile _psifile = PsiManager.getInstance(project).findFile(file);
    PsiFileWithStubSupport psiFile = null;

    if (_psifile != null && !(_psifile instanceof PsiPlainTextFile)) {
      _psifile = _psifile.getViewProvider().getStubBindingRoot();
      if (_psifile instanceof PsiFileWithStubSupport) {
        psiFile = (PsiFileWithStubSupport)_psifile;
        stubTree = psiFile.getStubTree();
        if (stubTree == null && psiFile instanceof PsiFileImpl) {
          stubTree = ((PsiFileImpl)psiFile).calcStubTree();
        }
      }
    }

    if (stubTree == null && psiFile == null) {
      return true;
    }
    if (stubTree == null) {
      ObjectStubTree objectStubTree = StubTreeLoader.getInstance().readFromVFile(project, file);
      if (objectStubTree == null) {
        return true;
      }
      stubTree = (StubTree)objectStubTree;
      final List<StubElement<?>> plained = stubTree.getPlainList();
      for (int i = 0, size = value.size(); i < size; i++) {
        final StubElement<?> stub = plained.get(value.get(i));
        PsiUtilCore.ensureValid(psiFile);
        final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);

        if (tree != null) {
          if (tree.getElementType() == stubType(stub)) {
            Psi psi = (Psi)tree.getPsi();
            PsiUtilCore.ensureValid(psi);
            if (!processor.process(psi)) return false;
          }
          else {
            String persistedStubTree = ((PsiFileStubImpl)stubTree.getRoot()).printTree();

            String stubTreeJustBuilt =
              ((PsiFileStubImpl)((IStubFileElementType)((PsiFileImpl)psiFile).getContentElementType()).getBuilder()
                .buildStubTree(psiFile)).printTree();

            StringBuilder builder = new StringBuilder();
            builder.append("Oops\n");


            builder.append("Recorded stub:-----------------------------------\n");
            builder.append(persistedStubTree);
            builder.append("\nAST built stub: ------------------------------------\n");
            builder.append(stubTreeJustBuilt);
            builder.append("\n");
            LOG.info(builder.toString());
            onInternalError(file);
          }
        }
      }
    }
    else {
      final List<StubElement<?>> plained = stubTree.getPlainList();
      for (int i = 0, size = value.size(); i < size; i++) {
        final int stubTreeIndex = value.get(i);
        if (stubTreeIndex >= plained.size()) {
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          StubTree stubTreeFromIndex = (StubTree)StubTreeLoader.getInstance().readFromVFile(project, file);
          LOG.error(stubTreeAndIndexDoNotMatch(stubTree, psiFile, plained, virtualFile, stubTreeFromIndex));

          onInternalError(file);

          break;
        }
        Psi psi = (Psi)plained.get(stubTreeIndex).getPsi();
        PsiUtilCore.ensureValid(psi);
        if (!processor.process(psi)) return false;
      }
    }
    return true;
  }

  /***
   * Returns a message to log when stub tree and index do not match
   */
  protected abstract String stubTreeAndIndexDoNotMatch(StubTree stubTree,
                                          PsiFileWithStubSupport psiFile,
                                          List<StubElement<?>> plained,
                                          VirtualFile virtualFile,
                                          StubTree stubTreeFromIndex);

  protected abstract void onInternalError(VirtualFile file);


}

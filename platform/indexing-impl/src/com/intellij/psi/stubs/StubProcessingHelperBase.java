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

  public <Psi extends PsiElement> boolean processStubsInFile(final Project project, final VirtualFile file, StubIdList value, final Processor<? super Psi> processor, Class<Psi> requiredClass) {
    return processStubsInFile(project, file, value, processor, requiredClass, false);
  }

  public <Psi extends PsiElement> boolean processStubsInFile(final Project project, final VirtualFile file, StubIdList value, final Processor<? super Psi> processor, Class<Psi> requiredClass, final boolean skipOnErrors) {
    StubTree stubTree = null;

    PsiFile candidatePsiFile = PsiManager.getInstance(project).findFile(file);
    PsiFileWithStubSupport psiFile = null;
    boolean customStubs = false;

    if (candidatePsiFile != null && !(candidatePsiFile instanceof PsiPlainTextFile)) {
      candidatePsiFile = candidatePsiFile.getViewProvider().getStubBindingRoot();
      if (candidatePsiFile instanceof PsiFileWithStubSupport) {
        psiFile = (PsiFileWithStubSupport)candidatePsiFile;
        stubTree = psiFile.getStubTree();
        if (stubTree == null && psiFile instanceof PsiFileImpl) {          
          IElementType contentElementType = ((PsiFileImpl)psiFile).getContentElementType();
          if (contentElementType instanceof IStubFileElementType) {
            stubTree = ((PsiFileImpl)psiFile).calcStubTree();
          }
          else {
            customStubs = true;
            assert BinaryFileStubBuilders.INSTANCE.forFileType(psiFile.getFileType()) != null;
          }
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
      if (customStubs && !(objectStubTree instanceof StubTree)) {
        return processor.process((Psi)psiFile); // e.g. dom indices
      }
      stubTree = (StubTree)objectStubTree;
      final List<StubElement<?>> plained = stubTree.getPlainList();
      for (int i = 0, size = value.size(); i < size; i++) {
        final int stubTreeIndex = value.get(i);
        if (stubTreeIndex >= plained.size()) {
          if (!skipOnErrors)
            onInternalError(file);

          break;
        }

        final StubElement<?> stub = plained.get(stubTreeIndex);
        PsiUtilCore.ensureValid(psiFile);
        final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);

        if (tree != null) {
          if (tree.getElementType() == stubType(stub)) {
            Psi psi = (Psi)tree.getPsi();
            PsiUtilCore.ensureValid(psi);
            if (!skipOnErrors && !requiredClass.isInstance(psi)) {
              inconsistencyDetected(stubTree, psiFile);
              break;
            }
            if (!processor.process(psi)) return false;
          }
          else if (!skipOnErrors) {
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
          if (!skipOnErrors) {
            inconsistencyDetected(stubTree, psiFile);
          }

          break;
        }
        Psi psi = (Psi)plained.get(stubTreeIndex).getPsi();
        PsiUtilCore.ensureValid(psi);
        if (!skipOnErrors && !requiredClass.isInstance(psi)) {
          inconsistencyDetected(stubTree, psiFile);
          break;
        }
        if (!processor.process(psi)) return false;
      }
    }
    return true;
  }

  private void inconsistencyDetected(StubTree stubTree, PsiFileWithStubSupport psiFile) {
    LOG.error(stubTreeAndIndexDoNotMatch(stubTree, psiFile));
    onInternalError(psiFile.getVirtualFile());
  }

  /***
   * Returns a message to log when stub tree and index do not match
   */
  protected abstract Object stubTreeAndIndexDoNotMatch(StubTree stubTree, PsiFileWithStubSupport psiFile);

  protected abstract void onInternalError(VirtualFile file);


}

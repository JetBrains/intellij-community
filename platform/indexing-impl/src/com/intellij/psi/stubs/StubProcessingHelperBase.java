/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
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

  public <Psi extends PsiElement> boolean processStubsInFile(@NotNull final Project project,
                                                             @NotNull final VirtualFile file,
                                                             @NotNull StubIdList value,
                                                             @NotNull final Processor<? super Psi> processor,
                                                             @NotNull Class<Psi> requiredClass) {
    return processStubsInFile(project, file, value, processor, requiredClass, false);
  }

  public <Psi extends PsiElement> boolean processStubsInFile(@NotNull final Project project,
                                                             @NotNull final VirtualFile file,
                                                             @NotNull StubIdList value,
                                                             @NotNull final Processor<? super Psi> processor,
                                                             @NotNull Class<Psi> requiredClass, final boolean skipOnErrors) {
    StubTree stubTree = null;

    PsiFile candidatePsiFile = PsiManager.getInstance(project).findFile(file);
    PsiFileWithStubSupport psiFile = null;
    boolean customStubs = false;

    if (candidatePsiFile != null && !(candidatePsiFile instanceof PsiPlainTextFile)) {
      final FileViewProvider viewProvider = candidatePsiFile.getViewProvider();
      final PsiFile stubBindingRoot = viewProvider.getStubBindingRoot();
      if (stubBindingRoot instanceof PsiFileWithStubSupport) {
        psiFile = (PsiFileWithStubSupport)stubBindingRoot;
        stubTree = psiFile.getStubTree();
        if (stubTree == null && psiFile instanceof PsiFileImpl) {
          IElementType contentElementType = ((PsiFileImpl)psiFile).getContentElementType();
          if (contentElementType instanceof IStubFileElementType) {
            stubTree = ((PsiFileImpl)psiFile).calcStubTree();
          }
          else {
            customStubs = true;
            if (BinaryFileStubBuilders.INSTANCE.forFileType(psiFile.getFileType()) == null) {
              //throw new AssertionError("unable to get stub builder for " + psiFile.getFileType() + "," + file);
              return true;
            }
          }
        }
      }
      if (!customStubs && stubTree != null) {
        final List<PsiFileStub> roots = new SmartList<PsiFileStub>(stubTree.getRoot());
        final List<Pair<IStubFileElementType, PsiFile>> stubbedRoots = StubTreeBuilder.getStubbedRoots(viewProvider);
        for (Pair<IStubFileElementType, PsiFile> stubbedRoot : stubbedRoots) {
          if (stubbedRoot.second == stubBindingRoot) continue;
          if (stubbedRoot.second instanceof PsiFileImpl) {
            StubTree secondaryStubTree = ((PsiFileImpl)stubbedRoot.second).getStubTree();
            if (secondaryStubTree == null) {
              secondaryStubTree = ((PsiFileImpl)stubbedRoot.second).calcStubTree();
            }
            final PsiFileStub root = secondaryStubTree.getRoot();
            roots.add(root);
          }
        }
        final PsiFileStub[] rootsArray = roots.toArray(new PsiFileStub[roots.size()]);
        for (PsiFileStub root : rootsArray) {
          if (root instanceof PsiFileStubImpl) {
            ((PsiFileStubImpl)root).setStubRoots(rootsArray);
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
      final List<StubElement<?>> plained = stubTree.getPlainListFromAllRoots();
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
              ((PsiFileStubImpl)((PsiFileImpl)psiFile).getElementTypeForStubBuilder().getBuilder()
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
      final List<StubElement<?>> plained = stubTree.getPlainListFromAllRoots();
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

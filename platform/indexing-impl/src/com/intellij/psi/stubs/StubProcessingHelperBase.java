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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
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
          IStubFileElementType elementType = ((PsiFileImpl)psiFile).getElementTypeForStubBuilder();
          if (elementType != null) {
            stubTree = ((PsiFileImpl)psiFile).calcStubTree();
          }
          else {
            customStubs = true;
            if (BinaryFileStubBuilders.INSTANCE.forFileType(psiFile.getFileType()) == null) {
              LOG.error("unable to get stub builder for " + psiFile.getFileType() + ", " +
                        StubTreeLoader.getFileViewProviderMismatchDiagnostics(viewProvider)
              );
            }
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
        if (!skipOnErrors && !requiredClass.isInstance(psiFile)) {
          inconsistencyDetected(objectStubTree, psiFile);
          return true;
        }
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

  public <Psi extends PsiElement> boolean processRawStubsInFile(@NotNull final Project project,
                                                                @NotNull final VirtualFile file,
                                                                @NotNull StubIdList value,
                                                                @NotNull final Processor<RawStub> processor,
                                                                @NotNull final Processor<? super Psi> fallbackProcessor,
                                                                @NotNull Class<Psi> requiredClass) {
    PsiFile candidatePsiFile = PsiManager.getInstance(project).findFile(file);

    if (candidatePsiFile != null) {
      final FileViewProvider viewProvider = candidatePsiFile.getViewProvider();
      final PsiFile stubBindingRoot = viewProvider.getStubBindingRoot();
      if (stubBindingRoot instanceof PsiFileImpl &&
          ((PsiFileImpl)stubBindingRoot).derefStub() == null &&
          ((PsiFileImpl)stubBindingRoot).getTreeElement() == null &&
          ((PsiFileImpl)stubBindingRoot).getElementTypeForStubBuilder() != null) {
        final List<Stub> stubs = StubTreeLoader.getInstance().readRawStubsFromVFile(project, file, value);
        if (stubs != null) {
          for (int i = 0; i < stubs.size(); i++) {
            Stub stub = stubs.get(i);
            if (!processor.process(new RawStub(stub, value.get(i), file))) return false;
          }
          return true;
        }
      }
    }

    return processStubsInFile(project, file, value, fallbackProcessor, requiredClass);
  }

  private void inconsistencyDetected(@NotNull ObjectStubTree stubTree, PsiFileWithStubSupport psiFile) {
    LOG.error(stubTreeAndIndexDoNotMatch(stubTree, psiFile));
    onInternalError(psiFile.getVirtualFile());
  }

  /***
   * Returns a message to log when stub tree and index do not match
   */
  protected abstract Object stubTreeAndIndexDoNotMatch(@NotNull ObjectStubTree stubTree, PsiFileWithStubSupport psiFile);

  protected abstract void onInternalError(VirtualFile file);


}

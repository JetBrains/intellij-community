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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Author: dmitrylomov
 */
public abstract class StubProcessingHelperBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubProcessingHelperBase");

  public <Psi extends PsiElement> boolean processStubsInFile(@NotNull Project project,
                                                      @NotNull VirtualFile file,
                                                      @NotNull StubIdList value,
                                                      @NotNull Processor<? super Psi> processor,
                                                      @NotNull Class<Psi> requiredClass) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      LOG.error("Stub index points to a file without PSI: " + file.getFileType());
      onInternalError(file);
      return true;
    }

    List<StubbedSpine> spines = getAllSpines(psiFile);
    if (spines.isEmpty()) {
      return handleNonPsiStubs(file, processor, requiredClass, psiFile);
    }

    for (int i = 0, size = value.size(); i < size; i++) {
      PsiElement psi = getStubPsi(spines, value.get(i));
      if (!checkType(requiredClass, psiFile, psi)) break;
      //noinspection unchecked
      if (!processor.process((Psi)psi)) return false;
    }
    return true;
  }

  @NotNull
  private static List<StubbedSpine> getAllSpines(PsiFile psiFile) {
    if (!(psiFile instanceof PsiFileImpl) && psiFile instanceof PsiFileWithStubSupport) {
      return Collections.singletonList(((PsiFileWithStubSupport)psiFile).getStubbedSpine());
    }
    
    return ContainerUtil.map(StubTreeBuilder.getStubbedRoots(psiFile.getViewProvider()), t -> ((PsiFileImpl)t.second).getStubbedSpine());
  }

  private <Psi extends PsiElement> boolean checkType(@NotNull Class<Psi> requiredClass, PsiFile psiFile, PsiElement psiElement) {
    if (requiredClass.isInstance(psiElement)) return true;
    
    StubTree stubTree = ((PsiFileWithStubSupport)psiFile).getStubTree();
    if (stubTree == null && psiFile instanceof PsiFileImpl) stubTree = ((PsiFileImpl)psiFile).calcStubTree();
    inconsistencyDetected(stubTree, (PsiFileWithStubSupport)psiFile);
    return false;
  }

  private static PsiElement getStubPsi(List<StubbedSpine> spines, int index) {
    if (spines.size() == 1) return spines.get(0).getStubPsi(index);

    for (StubbedSpine spine : spines) {
      int count = spine.getStubCount();
      if (index < count) {
        return spine.getStubPsi(index);
      }
      index -= count;
    }
    return null;
  }

  // e.g. DOM indices
  private <Psi extends PsiElement> boolean handleNonPsiStubs(@NotNull VirtualFile file,
                                                             @NotNull Processor<? super Psi> processor,
                                                             @NotNull Class<Psi> requiredClass,
                                                             @NotNull PsiFile psiFile) {
    if (BinaryFileStubBuilders.INSTANCE.forFileType(psiFile.getFileType()) == null) {
      LOG.error("unable to get stub builder for " + psiFile.getFileType() + ", " +
                StubTreeLoader.getFileViewProviderMismatchDiagnostics(psiFile.getViewProvider()));
      onInternalError(file);
      return true;
    }

    if (psiFile instanceof PsiBinaryFile) {
      // a file can be indexed as containing stubs, 
      // but then in a specific project FileViewProviderFactory can decide not to create stub-aware PSI 
      // because the file isn't in expected location
      return true; 
    }

    ObjectStubTree objectStubTree = StubTreeLoader.getInstance().readFromVFile(psiFile.getProject(), file);
    if (objectStubTree == null) {
      LOG.error("Stub index points to a file without indexed stubs: " + psiFile.getFileType());
      onInternalError(file);
      return true;
    }
    if (objectStubTree instanceof StubTree) {
      LOG.error("Stub index points to a file with PSI stubs (instead of non-PSI ones): " + psiFile.getFileType());
      onInternalError(file);
      return true;
    }
    if (!requiredClass.isInstance(psiFile)) {
      inconsistencyDetected(objectStubTree, (PsiFileWithStubSupport)psiFile);
      return true;
    }
    //noinspection unchecked
    return processor.process((Psi)psiFile);
  }

  private void inconsistencyDetected(@Nullable ObjectStubTree stubTree, @NotNull PsiFileWithStubSupport psiFile) {
    try {
      StubTextInconsistencyException.checkStubTextConsistency(psiFile);
      LOG.error(StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch("PSI and index do not match.", stubTree, psiFile));
    }
    finally {
      onInternalError(psiFile.getVirtualFile());
    }
  }

  protected abstract void onInternalError(VirtualFile file);


}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubInconsistencyReporter.EnforcedInconsistencyType;
import com.intellij.psi.stubs.StubInconsistencyReporter.SourceOfCheck;
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
  protected static final Logger LOG = Logger.getInstance(StubProcessingHelperBase.class);

  public <Psi extends PsiElement> boolean processStubsInFile(@NotNull Project project,
                                                             @NotNull VirtualFile file,
                                                             @NotNull StubIdList value,
                                                             @NotNull Processor<? super Psi> processor,
                                                             @Nullable GlobalSearchScope scope,
                                                             @NotNull Class<Psi> requiredClass) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      LOG.error("Stub index points to a file without PSI: " +
                getFileTypeInfo(file, project) + ", " +
                "indexing stamp info = " + StubTreeLoader.getInstance().getIndexingStampInfo(file) + ", " +
                "used scope = " + scope);
      onInternalError(file);
      return true;
    }

    if (value.size() == 1 && value.get(0) == 0) {
      //noinspection unchecked
      return !checkType(requiredClass, psiFile, psiFile) || processor.process((Psi)psiFile);
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

  private static @NotNull List<StubbedSpine> getAllSpines(PsiFile psiFile) {
    if (!(psiFile instanceof PsiFileImpl) && psiFile instanceof PsiFileWithStubSupport) {
      return Collections.singletonList(((PsiFileWithStubSupport)psiFile).getStubbedSpine());
    }

    return ContainerUtil.map(StubTreeBuilder.getStubbedRoots(psiFile.getViewProvider()), t -> ((PsiFileImpl)t.second).getStubbedSpine());
  }

  private <Psi extends PsiElement> boolean checkType(@NotNull Class<Psi> requiredClass, PsiFile psiFile, @Nullable PsiElement psiElement) {
    if (requiredClass.isInstance(psiElement)) return true;

    String extraMessage = "psiElement is not instance of requiredClass.\n" +
                          "psiElement=" + psiElement +
                          (psiElement != null ? ", psiElement.class=" + psiElement.getClass() : "") +
                          ", requiredClass=" + requiredClass +
                          ".\nref: 50cf572587cf";

    StubTree stubTree = ((PsiFileWithStubSupport)psiFile).getStubTree();
    if (stubTree == null && psiFile instanceof PsiFileImpl) stubTree = ((PsiFileImpl)psiFile).calcStubTree();
    inconsistencyDetected(stubTree, (PsiFileWithStubSupport)psiFile, extraMessage);
    return false;
  }

  private static PsiElement getStubPsi(List<? extends StubbedSpine> spines, int index) {
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
      LOG.error("unable to get stub builder for file with " + getFileTypeInfo(file, psiFile.getProject()) + ", " +
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
      LOG.error("Stub index points to a file without indexed stubs: " + getFileTypeInfo(file, psiFile.getProject()));
      onInternalError(file);
      return true;
    }
    if (objectStubTree instanceof StubTree) {
      LOG.error("Stub index points to a file with PSI stubs (instead of non-PSI ones): " + getFileTypeInfo(file, psiFile.getProject()));
      onInternalError(file);
      return true;
    }
    if (!requiredClass.isInstance(psiFile)) {
      String extraMessage = "psiFile is not instance of requiredClass.\n" +
                            "psiFile=" + psiFile +
                            ", psiFile.class=" + psiFile.getClass() +
                            ", requiredClass=" + requiredClass +
                            ".\nref: 50cf572587cf";
      inconsistencyDetected(objectStubTree, (PsiFileWithStubSupport)psiFile, extraMessage);
      return true;
    }
    //noinspection unchecked
    return processor.process((Psi)psiFile);
  }

  private void inconsistencyDetected(@Nullable ObjectStubTree stubTree,
                                     @NotNull PsiFileWithStubSupport psiFile,
                                     @NotNull String extraMessage) {
    try {
      StubTextInconsistencyException.checkStubTextConsistency(psiFile,
                                                              SourceOfCheck.WrongTypePsiInStubHelper,
                                                              EnforcedInconsistencyType.PsiOfUnexpectedClass);
      LOG.error(extraMessage + "\n" + StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(stubTree, psiFile, null));
    }
    finally {
      onInternalError(psiFile.getVirtualFile());
    }
  }

  protected abstract void onInternalError(VirtualFile file);

  protected static @NotNull String getFileTypeInfo(@NotNull VirtualFile file, @NotNull Project project) {
    return "file = "+file + (file.isValid() ? "" : " (invalid)") +", " +
           "file type = " + file.getFileType() + ", " +
           "indexed file type = " + FileTypeIndex.getIndexedFileType(file, project);
  }

}

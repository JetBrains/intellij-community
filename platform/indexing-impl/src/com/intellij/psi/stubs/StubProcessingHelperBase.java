// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.search.*;
import com.intellij.psi.stubs.StubInconsistencyReporter.SourceOfCheck;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.stubs.StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource.*;

/**
 * Author: dmitrylomov
 */
@ApiStatus.Internal
public abstract class StubProcessingHelperBase {
  protected static final Logger LOG = Logger.getInstance(StubProcessingHelperBase.class);

  public <Psi extends PsiElement> boolean processStubsInFile(@NotNull Project project,
                                                             @NotNull VirtualFile file,
                                                             @NotNull StubIdList value,
                                                             @NotNull Processor<? super Psi> processor,
                                                             @Nullable GlobalSearchScope scope,
                                                             @NotNull Class<Psi> requiredClass,
                                                             @NotNull Computable<String> debugOperationName) {
    CodeInsightContext context = getCodeInsightContext(file, project, scope);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file, context);

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
      return !checkType(requiredClass, psiFile, psiFile, debugOperationName, value, 0, ZeroStubIdList) || processor.process((Psi)psiFile);
    }

    List<StubbedSpine> spines = getAllSpines(psiFile);
    if (spines.isEmpty()) {
      return handleNonPsiStubs(file, processor, requiredClass, psiFile);
    }

    for (int i = 0, size = value.size(); i < size; i++) {
      PsiElement psi = getStubPsi(spines, value.get(i));
      if (!checkType(requiredClass, psiFile, psi, debugOperationName, value, i, StubPsiCheck)) break;
      //noinspection unchecked
      if (!processor.process((Psi)psi)) return false;
    }
    return true;
  }

  private static @NotNull CodeInsightContext getCodeInsightContext(@NotNull VirtualFile file, @NotNull Project project, @Nullable GlobalSearchScope scope) {
    if (!CodeInsightContextKt.isSharedSourceSupportEnabled(project)) {
      return CodeInsightContextKt.anyContext();
    }

    if (scope == null) {
      return CodeInsightContextKt.anyContext();
    }

    CodeInsightContextFileInfo fileInfo = CodeInsightContextAwareSearchScopesKt.getFileContextInfo(scope, file);
    if (fileInfo instanceof ActualContextFileInfo) {
      Collection<CodeInsightContext> contexts = ((ActualContextFileInfo)fileInfo).getContexts();
      if (contexts.size() > 1) {
        // todo ijpl-339 we need to process the file twice in this case. Not supported yet
        LOG.error("Multiple contexts for file " + file + " in scope " + scope + ". Contexts: " + contexts);
      }
      return contexts.iterator().next();
    }
    if (fileInfo instanceof NoContextFileInfo) {
      return CodeInsightContextKt.anyContext();
    }
    // fileInfo instanceof DoesNotContainFileInfo
    LOG.error("Provided scope does not contain file " + file + ", scope = " + scope);
    return CodeInsightContextKt.anyContext();
  }

  private static @Unmodifiable @NotNull List<StubbedSpine> getAllSpines(PsiFile psiFile) {
    if (!(psiFile instanceof PsiFileImpl) && psiFile instanceof PsiFileWithStubSupport) {
      return Collections.singletonList(((PsiFileWithStubSupport)psiFile).getStubbedSpine());
    }

    List<Pair<LanguageStubDescriptor, PsiFile>> roots = StubTreeBuilder.getStubbedRootDescriptors(psiFile.getViewProvider());
    return ContainerUtil.map(roots, pair -> {
      PsiFileImpl root = (PsiFileImpl)pair.second;
      return root.getStubbedSpine();
    });
  }

  private <Psi extends PsiElement> boolean checkType(@NotNull Class<Psi> requiredClass, PsiFile psiFile, @Nullable PsiElement psiElement,
                                                     @NotNull Computable<String> debugOperationName,
                                                     @NotNull StubIdList debugStubIdList, int stubIdListIdx,
                                                     @NotNull StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource source) {
    if (requiredClass.isInstance(psiElement)) return true;

    String extraMessage = "psiElement is not instance of requiredClass.\n" +
                          "psiElement=" + psiElement +
                          (psiElement != null ? ", psiElement.class=" + psiElement.getClass() : "") +
                          ", requiredClass=" + requiredClass +
                          ", operation=" + debugOperationName.get() +
                          ", stubIdList=" + debugStubIdList + "@" + stubIdListIdx +
                          ".\nref: 20250127";

    StubTree stubTree = ((PsiFileWithStubSupport)psiFile).getStubTree();
    if (stubTree == null && psiFile instanceof PsiFileImpl) stubTree = ((PsiFileImpl)psiFile).calcStubTree();
    inconsistencyDetected(stubTree, (PsiFileWithStubSupport)psiFile, extraMessage, source);
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
      // because the file isn't in the expected location
      return true;
    }

    ObjectStubTree<?> objectStubTree = StubTreeLoader.getInstance().readFromVFile(psiFile.getProject(), file);
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
                            ".\nref: 20250127";
      inconsistencyDetected(objectStubTree, (PsiFileWithStubSupport)psiFile, extraMessage, WrongPsiFileClassInNonPsiStub);
      return true;
    }
    //noinspection unchecked
    return processor.process((Psi)psiFile);
  }

  private void inconsistencyDetected(
    @Nullable ObjectStubTree<?> stubTree,
    @NotNull PsiFileWithStubSupport psiFile,
    @NotNull String extraMessage,
    @NotNull StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource source
  ) {
    try {
      StubTextInconsistencyException.checkStubTextConsistency(psiFile, SourceOfCheck.WrongTypePsiInStubHelper);
      String dumbState = DumbService.isDumb(psiFile.getProject()) ?
                         "\ndumbMode,dumbModeAccessType=" + FileBasedIndex.getInstance().getCurrentDumbModeAccessType(null) :
                         "\nno dumbMode";

      LOG.error(extraMessage + dumbState + "\n" + StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(stubTree, psiFile, null, source));
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable t) {
      onInternalError(psiFile.getVirtualFile());
      throw t;
    }
  }

  protected abstract void onInternalError(VirtualFile file);

  protected static @NotNull String getFileTypeInfo(@NotNull VirtualFile file, @NotNull Project project) {
    return "file = "+file + (file.isValid() ? "" : " (invalid)") +", " +
           "file type = " + file.getFileType() + ", " +
           "indexed file type = " + FileTypeIndex.getIndexedFileType(file, project);
  }

}

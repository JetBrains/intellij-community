// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IndexCacheManagerImpl implements CacheManager {
  @NotNull
  private final Project myProject;

  public IndexCacheManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public PsiFile @NotNull [] getFilesWithWord(@NotNull String word,
                                              short occurenceMask,
                                              @NotNull GlobalSearchScope scope,
                                              boolean caseSensitively) {
    if (myProject.isDefault()) {
      return PsiFile.EMPTY_ARRAY;
    }
    List<PsiFile> result = new ArrayList<>();
    Processor<PsiFile> processor = Processors.cancelableCollectProcessor(result);

    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return result.isEmpty() ? PsiFile.EMPTY_ARRAY : result.toArray(PsiFile.EMPTY_ARRAY);
  }

  @Override
  public VirtualFile @NotNull [] getVirtualFilesWithWord(@NotNull String word,
                                                         short occurenceMask,
                                                         @NotNull GlobalSearchScope scope,
                                                         boolean caseSensitively) {
    if (myProject.isDefault()) {
      return VirtualFile.EMPTY_ARRAY;
    }

    List<VirtualFile> result = new ArrayList<>(5);
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      collectVirtualFilesWithWord(word, occurenceMask, scope, caseSensitively, processor);
    });
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public boolean processVirtualFilesWithAllWords(@NotNull Collection<String> words,
                                                 short occurenceMask,
                                                 @NotNull GlobalSearchScope scope,
                                                 boolean caseSensitively,
                                                 @NotNull Processor<? super VirtualFile> processor) {
    if (myProject.isDefault()) {
      return true;
    }
    PsiSearchHelperImpl.TextIndexQuery query = PsiSearchHelperImpl.TextIndexQuery.fromWords(words, caseSensitively, false, occurenceMask);

    if (PsiSearchHelperImpl.LOG.isTraceEnabled()) {
      PsiSearchHelperImpl.LOG.trace("searching for words " + words + " in " + scope);
    }

    try {
      return FileBasedIndex.getInstance().processFilesContainingAllKeys(query.toFileBasedIndexQueries(), scope, processor);
    }
    catch (IndexNotReadyException e) {
      throw new ProcessCanceledException();
    }
  }

  // IMPORTANT!!!
  // Since implementation of virtualFileProcessor.process() may call indices directly or indirectly,
  // we cannot call it inside FileBasedIndex.processValues() method except in collecting form
  // If we do, deadlocks are possible (IDEADEV-42137). Process the files without not holding indices' read lock.
  private void collectVirtualFilesWithWord(@NotNull String word,
                                           short occurrenceMask,
                                           @NotNull GlobalSearchScope scope,
                                           boolean caseSensitively,
                                           @NotNull Processor<? super VirtualFile> fileProcessor) {
    processVirtualFilesWithAllWords(Collections.singleton(word), occurrenceMask, scope, caseSensitively, fileProcessor);
  }

  @Override
  public boolean processFilesWithWord(@NotNull Processor<? super PsiFile> psiFileProcessor, @NotNull String word, short occurrenceMask, @NotNull GlobalSearchScope scope, boolean caseSensitively) {
    List<VirtualFile> result = new ArrayList<>(5);
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
    collectVirtualFilesWithWord(word, occurrenceMask, scope, caseSensitively, processor);
    if (result.isEmpty()) return true;

    PsiManager psiManager = PsiManager.getInstance(myProject);
    Processor<VirtualFile> virtualFileProcessor = new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(VirtualFile virtualFile) {
        if (virtualFile.isValid()) {
          PsiFile psiFile = psiManager.findFile(virtualFile);
          return psiFile == null || psiFileProcessor.process(psiFile);
        }
        return true;
      }
    };


    // IMPORTANT!!!
    // Since implementation of virtualFileProcessor.process() may call indices directly or indirectly,
    // we cannot call it inside FileBasedIndex.processValues() method
    // If we do, deadlocks are possible (IDEADEV-42137). So first we obtain files with the word specified,
    // and then process them not holding indices' read lock.
    for (VirtualFile vFile : result) {
      ProgressIndicatorProvider.checkCanceled();
      if (!virtualFileProcessor.process(vFile)) {
        return false;
      }
    }
    return true;
  }
}

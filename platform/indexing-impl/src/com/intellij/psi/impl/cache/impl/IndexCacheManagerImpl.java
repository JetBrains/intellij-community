/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class IndexCacheManagerImpl implements CacheManager{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.IndexCacheManagerImpl");
  private final Project myProject;
  private final PsiManager myPsiManager;

  public IndexCacheManagerImpl(PsiManager psiManager) {
    myPsiManager = psiManager;
    myProject = psiManager.getProject();
  }

  @Override
  @NotNull
  public PsiFile[] getFilesWithWord(@NotNull final String word, final short occurenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    if (myProject.isDefault()) {
      return PsiFile.EMPTY_ARRAY;
    }
    List<PsiFile> result = new ArrayList<>();
    Processor<PsiFile> processor = Processors.cancelableCollectProcessor(result);

    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return result.isEmpty() ? PsiFile.EMPTY_ARRAY : result.toArray(PsiFile.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public VirtualFile[] getVirtualFilesWithWord(@NotNull final String word, final short occurenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    if (myProject.isDefault()) {
      return VirtualFile.EMPTY_ARRAY;
    }

    final List<VirtualFile> result = new ArrayList<>(5);
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
    collectVirtualFilesWithWord(word, occurenceMask, scope, caseSensitively, processor);
    return result.isEmpty() ? VirtualFile.EMPTY_ARRAY : result.toArray(new VirtualFile[result.size()]);
  }

  // IMPORTANT!!!
  // Since implementation of virtualFileProcessor.process() may call indices directly or indirectly,
  // we cannot call it inside FileBasedIndex.processValues() method except in collecting form
  // If we do, deadlocks are possible (IDEADEV-42137). Process the files without not holding indices' read lock.
  private boolean collectVirtualFilesWithWord(@NotNull final String word,
                                              final short occurrenceMask,
                                              @NotNull final GlobalSearchScope scope,
                                              final boolean caseSensitively,
                                              @NotNull final Processor<VirtualFile> fileProcessor) {
    if (myProject.isDefault()) {
      return true;
    }

    try {
      return ReadAction.compute(() -> FileBasedIndex.getInstance()
        .processValues(IdIndex.NAME, new IdIndexEntry(word, caseSensitively), null, new FileBasedIndex.ValueProcessor<Integer>() {
          final FileIndexFacade index = FileIndexFacade.getInstance(myProject);

          @Override
          public boolean process(@NotNull final VirtualFile file, final Integer value) {
            ProgressIndicatorProvider.checkCanceled();
            final int mask = value.intValue();
            if ((mask & occurrenceMask) != 0 && index.shouldBeFound(scope, file)) {
              if (!fileProcessor.process(file)) return false;
            }
            return true;
          }
        }, scope));
    }
    catch (IndexNotReadyException e) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public boolean processFilesWithWord(@NotNull final Processor<PsiFile> psiFileProcessor, @NotNull final String word, final short occurrenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    final List<VirtualFile> result = new ArrayList<>(5);
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
    collectVirtualFilesWithWord(word, occurrenceMask, scope, caseSensitively, processor);
    if (result.isEmpty()) return true;

    final Processor<VirtualFile> virtualFileProcessor = new ReadActionProcessor<VirtualFile>() {
      @Override
      public boolean processInReadAction(VirtualFile virtualFile) {
        if (virtualFile.isValid()) {
          final PsiFile psiFile = myPsiManager.findFile(virtualFile);
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

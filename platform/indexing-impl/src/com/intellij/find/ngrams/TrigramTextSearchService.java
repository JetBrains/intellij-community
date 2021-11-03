// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.ngrams;

import com.intellij.find.TextSearchService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class TrigramTextSearchService implements TextSearchService {
  @Override
  public @NotNull TextSearchResult processFilesWithText(@NotNull String text,
                                                        Processor<? super VirtualFile> processor,
                                                        @NotNull GlobalSearchScope scope) {
    IntSet keys = new IntOpenHashSet();
    TrigramBuilder.processTrigrams(text, new TrigramBuilder.TrigramProcessor() {
      @Override
      public boolean test(int value) {
        keys.add(value);
        return true;
      }
    });
    if (keys.isEmpty()) return TextSearchResult.NO_TRIGRAMS;
    return FileBasedIndex.getInstance().getFilesWithKey(TrigramIndex.INDEX_ID, keys, f -> {
      ProgressManager.checkCanceled();
      return processor.process(f);
    }, scope)
           ? TextSearchResult.FINISHED
           : TextSearchResult.STOPPED;
  }

  @Override
  public boolean isInSearchableScope(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return !file.isDirectory() &&
           TrigramIndex.isIndexable(fileType) &&
           !ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType) &&
           !SingleRootFileViewProvider.isTooLargeForIntelligence(file);
  }

  @ApiStatus.Internal
  public static boolean useIndexingSearchExtensions() {
    return Registry.is("find.use.indexing.searcher.extensions");
  }
}

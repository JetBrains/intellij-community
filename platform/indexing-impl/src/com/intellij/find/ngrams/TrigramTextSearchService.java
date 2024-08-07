// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams;

import com.intellij.find.TextSearchService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TrigramTextSearchService implements TextSearchService {
  @Override
  public @NotNull TextSearchResult processFilesWithText(@NotNull String text,
                                                        @NotNull Processor<? super VirtualFile> processor,
                                                        @NotNull GlobalSearchScope scope) {
    IntSet keys = TrigramBuilder.getTrigrams(text);
    if (keys.isEmpty()) return TextSearchResult.NO_TRIGRAMS;
    return FileBasedIndex.getInstance().getFilesWithKey(TrigramIndex.INDEX_ID, keys, f -> {
      ProgressManager.checkCanceled();
      return processor.process(f);
    }, scope)
           ? TextSearchResult.FINISHED
           : TextSearchResult.STOPPED;
  }

  @Override
  public boolean isInSearchableScope(@NotNull VirtualFile file, @NotNull Project project) {
    FileType fileType = file.getFileType();
    return !file.isDirectory() &&
           TrigramIndex.isIndexable(file, project) &&
           !ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType) &&
           !SingleRootFileViewProvider.isTooLargeForIntelligence(file);
  }

  public static boolean useIndexingSearchExtensions() {
    return Boolean.parseBoolean(System.getProperty("find.use.indexing.searcher.extensions", "true"));
  }
}

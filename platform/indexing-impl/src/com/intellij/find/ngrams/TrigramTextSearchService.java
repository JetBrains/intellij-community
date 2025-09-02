// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams;

import com.intellij.find.TextSearchService;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.indexing.IndexedFileImpl;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.SystemProperties.getBooleanProperty;

@ApiStatus.Internal
public final class TrigramTextSearchService implements TextSearchService {

  private static final boolean USE_INDEX_FOR_SEARCH = getBooleanProperty("find.use.indexing.searcher.extensions", true);

  private final TrigramIndexFilter trigramIndexFilter = ApplicationManager.getApplication().getService(TrigramIndexFilter.class);

  @Override
  public @NotNull TextSearchResult processFilesWithText(@NotNull String text,
                                                        @NotNull Processor<? super VirtualFile> processor,
                                                        @NotNull GlobalSearchScope scope) {
    IntSet keys = TrigramBuilder.getTrigrams(text);
    if (keys.isEmpty()) {
      return TextSearchResult.NO_TRIGRAMS;
    }

    boolean fullyCompleted = FileBasedIndex.getInstance().getFilesWithKey(TrigramIndex.INDEX_ID, keys, file -> {
      //TODO RC: checkCanceled() is not needed here, since all processors are already wrapped in cancellableProcessor()
      ProgressManager.checkCanceled();
      return processor.process(file);
    }, scope);
    return fullyCompleted ?
           TextSearchResult.FINISHED :
           TextSearchResult.STOPPED;
  }

  @Override
  public boolean isInSearchableScope(@NotNull VirtualFile file,
                                     @NotNull Project project) {
    FileType fileType = file.getFileType();
    return !file.isDirectory()
           && isIndexable(file, project)
           && !ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType)
           && !SingleRootFileViewProvider.isTooLargeForIntelligence(file);
  }

  /** @return true if search by index available */
  public static boolean useIndexingSearchExtensions() {
    //TODO RC: it's unclear that .useIndexingSearchExtensions() means: it is either 'use _any_ indexing extension/engine
    //         available', or 'use trigram indexing engine specifically'.
    //         Currently there is only one indexing extension (=searcher) available, so effectively there is no difference
    //         between these two cases _now_ -- but it could be the difference in future, then >1 searcher may become be available.
    return USE_INDEX_FOR_SEARCH;
  }

  private boolean isIndexable(@NotNull VirtualFile file,
                              @NotNull Project project) {
    IndexedFileImpl indexedFile = new IndexedFileImpl(file, project);
    return trigramIndexFilter.acceptInput(indexedFile);
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindInProjectSearchEngine;
import com.intellij.find.FindModel;
import com.intellij.find.TextSearchService;
import com.intellij.find.TextSearchService.TextSearchResult;
import com.intellij.find.ngrams.TrigramTextSearchService;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.*;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Search engine implementation based on {@link com.intellij.find.ngrams.TrigramIndex} */
@ApiStatus.Internal
public final class IdeaIndexBasedFindInProjectSearchEngine implements FindInProjectSearchEngine {
  @Override
  public @NotNull FindInProjectSearcher createSearcher(@NotNull FindModel findModel,
                                                       @NotNull Project project) {
    return new FindInProjectByIndexSearcher(project, findModel);
  }

  private static final class FindInProjectByIndexSearcher implements FindInProjectSearcher {

    private final @NotNull FindModel findModel;

    private final @NotNull TextSearchService textSearchService = TextSearchService.getInstance();

    private final @NotNull Project project;
    private final @NotNull ProjectFileIndex fileIndex;

    /**
     * If findModel's search pattern is a regexp -- trigram index could still be used to provide candidate files.
     * Basically: we build a lookup string from 'static' parts of the regexp pattern.
     * (We still use the actual regexp pattern to look inside candidate files)
     */
    private final String stringToFindInIndices;
    /** Has stringToFindInIndices at least one trigram? If false -- trigram index can't be used to provide candidates. */
    private final boolean hasTrigrams;

    FindInProjectByIndexSearcher(@NotNull Project project,
                                 @NotNull FindModel findModel) {
      this.project = project;
      this.findModel = findModel;
      this.fileIndex = ProjectFileIndex.getInstance(project);

      String stringToFind = findModel.getStringToFind();
      stringToFindInIndices = findModel.isRegularExpressions() ?
                              FindInProjectUtil.buildStringToFindForIndicesFromRegExp(stringToFind, project) :
                              stringToFind;

      hasTrigrams = hasTrigrams(stringToFindInIndices);
    }

    @Override
    public @NotNull Collection<VirtualFile> searchForOccurrences() {
      if (!TrigramTextSearchService.useIndexingSearchExtensions()) {
        return Collections.emptyList();
      }

      return ReadAction
        .nonBlocking(this::doSearchForOccurrences)
        .withDocumentsCommitted(project)
        .executeSynchronously();
    }

    private Collection<VirtualFile> doSearchForOccurrences() {
      if (stringToFindInIndices.isEmpty()) {
        return Collections.emptySet();
      }

      GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
        FindInProjectUtil.getScopeFromModel(project, findModel),
        project
      );

      List<VirtualFile> hits = new ArrayList<>();
      TextSearchResult result = DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(
        () -> {
          return textSearchService.processFilesWithText(
            stringToFindInIndices,
            Processors.cancelableCollectProcessor(hits),
            scope
          );
        }
      );
      if (result != TextSearchResult.NO_TRIGRAMS) {
        return Collections.unmodifiableCollection(hits);
      }

      PsiSearchHelper helper = PsiSearchHelper.getInstance(project);
      CacheManager cacheManager = CacheManager.getInstance(project);

      return DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
        Set<VirtualFile> resultFiles = new HashSet<>();

        helper.processCandidateFilesForText(scope, UsageSearchContext.ANY, findModel.isCaseSensitive(), stringToFindInIndices, file -> {
          ContainerUtil.addIfNotNull(resultFiles, file);
          return true;
        });

        // in case our word splitting is incorrect
        VirtualFile[] filesWithWord = cacheManager.getVirtualFilesWithWord(
          stringToFindInIndices,
          UsageSearchContext.ANY,
          scope,
          findModel.isCaseSensitive()
        );

        Collections.addAll(resultFiles, filesWithWord);
        return Collections.unmodifiableCollection(resultFiles);
      });
    }

    @Override
    public boolean isReliable() {
      if (!TrigramTextSearchService.useIndexingSearchExtensions()) {
        return false;
      }

      if (DumbService.isDumb(project)) {
        return false;
      }

      //MAYBE RC: TrigramIndexFilter.isExcludeExtensionsEnabled() -> return false?
      //          I'm not sure do we need this: if some files are not indexed -> they are rejected by TrigramIndexFilter, hence
      //          isCovered(file) returns false -> which is enough to still scan the file in 2nd (bruteforce) search phase.
      //          So, it seems, this condition is not needed at all?

      // a local scope may be over a non-indexed file
      if (findModel.getCustomScope() instanceof LocalSearchScope) return false;

      if (hasTrigrams) return true; //RC: why it is enough to hasTrigrams to be reliable?

      // $ is used to separate words when indexing plain-text files but not when indexing
      // Java identifiers, so we can't consistently break a string containing $ characters into words
      return findModel.isWholeWordsOnly()
             && stringToFindInIndices.indexOf('$') < 0
             && !StringUtil.getWordsIn(stringToFindInIndices).isEmpty();
    }

    @Override
    public boolean isCovered(@NotNull VirtualFile file) {
      return hasTrigrams
             && isCoveredByIndex(file)
             && (fileIndex.isInContent(file) || fileIndex.isInLibrary(file));
    }

    private boolean isCoveredByIndex(@NotNull VirtualFile file) {
      //i.e. if .java-files are not indexed -- isCoveredByIndex returns false
      return textSearchService.isInSearchableScope(file, project);
    }

    private static boolean hasTrigrams(@NotNull String text) {
      return !TrigramBuilder.getTrigrams(text).isEmpty();
    }
  }
}

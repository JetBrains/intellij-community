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
import com.intellij.openapi.util.registry.Registry;
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
    private final @NotNull TextSearchService myTextSearchService = TextSearchService.getInstance();
    private final @NotNull Project myProject;
    private final @NotNull ProjectFileIndex myFileIndex;
    private final @NotNull FindModel myFindModel;

    /**
     * If findModel's search pattern is a regexp -- trigram index could still be used to provide candidate files.
     * Basically: we build a lookup string from 'static' parts of the regexp pattern.
     * (We still use the actual regexp pattern to look inside candidate files)
     */
    private final String myStringToFindInIndices;
    /** Has pattern at least one trigram? If false -- trigram index can't be used to provide candidates. */
    private final boolean myHasTrigrams;

    FindInProjectByIndexSearcher(@NotNull Project project,
                                 @NotNull FindModel findModel) {
      myProject = project;
      myFindModel = findModel;
      myFileIndex = ProjectFileIndex.getInstance(myProject);

      String stringToFind = findModel.getStringToFind();
      myStringToFindInIndices = findModel.isRegularExpressions() ?
                                FindInProjectUtil.buildStringToFindForIndicesFromRegExp(stringToFind, project) :
                                stringToFind;

      myHasTrigrams = hasTrigrams(myStringToFindInIndices);
    }

    @Override
    public @NotNull Collection<VirtualFile> searchForOccurrences() {
      return ReadAction
        .nonBlocking(this::doSearchForOccurrences)
        .withDocumentsCommitted(myProject)
        .executeSynchronously();
    }

    public Collection<VirtualFile> doSearchForOccurrences() {
      if (myStringToFindInIndices.isEmpty()) {
        return Collections.emptySet();
      }

      GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
        FindInProjectUtil.getScopeFromModel(myProject, myFindModel),
        myProject
      );

      List<VirtualFile> hits = new ArrayList<>();
      TextSearchResult result = DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(
        () -> {
          return myTextSearchService.processFilesWithText(
            myStringToFindInIndices,
            Processors.cancelableCollectProcessor(hits),
            scope
          );
        }
      );
      if (result != TextSearchResult.NO_TRIGRAMS) {
        return Collections.unmodifiableCollection(hits);
      }

      PsiSearchHelper helper = PsiSearchHelper.getInstance(myProject);
      CacheManager cacheManager = CacheManager.getInstance(myProject);

      return DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
        Set<VirtualFile> resultFiles = new HashSet<>();

        helper.processCandidateFilesForText(scope, UsageSearchContext.ANY, myFindModel.isCaseSensitive(), myStringToFindInIndices, file -> {
          ContainerUtil.addIfNotNull(resultFiles, file);
          return true;
        });

        // in case our word splitting is incorrect
        VirtualFile[] filesWithWord = cacheManager.getVirtualFilesWithWord(
          myStringToFindInIndices,
          UsageSearchContext.ANY,
          scope,
          myFindModel.isCaseSensitive()
        );

        Collections.addAll(resultFiles, filesWithWord);
        return Collections.unmodifiableCollection(resultFiles);
      });
    }

    @Override
    public boolean isReliable() {
      if (DumbService.isDumb(myProject)) {
        return false;
      }
      if (!TrigramTextSearchService.useIndexingSearchExtensions()) {
        return false;
      }

      // a local scope may be over a non-indexed file
      if (myFindModel.getCustomScope() instanceof LocalSearchScope) return false;

      if (myHasTrigrams) return true;

      // $ is used to separate words when indexing plain-text files but not when indexing
      // Java identifiers, so we can't consistently break a string containing $ characters into words
      return myFindModel.isWholeWordsOnly()
             && myStringToFindInIndices.indexOf('$') < 0
             && !StringUtil.getWordsIn(myStringToFindInIndices).isEmpty();
    }

    @Override
    public boolean isCovered(@NotNull VirtualFile file) {
      return myHasTrigrams
             && isCoveredByIndex(file)
             && (myFileIndex.isInContent(file) || myFileIndex.isInLibrary(file));
    }

    private boolean isCoveredByIndex(@NotNull VirtualFile file) {
      return myTextSearchService.isInSearchableScope(file, myProject);
    }

    private static boolean hasTrigrams(@NotNull String text) {
      return !TrigramBuilder.getTrigrams(text).isEmpty();
    }
  }
}

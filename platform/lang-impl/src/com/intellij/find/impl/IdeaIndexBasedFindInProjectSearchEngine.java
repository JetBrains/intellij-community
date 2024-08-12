// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindInProjectSearchEngine;
import com.intellij.find.FindModel;
import com.intellij.find.TextSearchService;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.*;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class IdeaIndexBasedFindInProjectSearchEngine implements FindInProjectSearchEngine {
  @Override
  public @NotNull FindInProjectSearcher createSearcher(@NotNull FindModel findModel, @NotNull Project project) {
    return new MyFindInProjectSearcher(project, findModel);
  }

  private static final class MyFindInProjectSearcher implements FindInProjectSearcher {
    private final @NotNull ProjectFileIndex myFileIndex;
    private final @NotNull Project myProject;
    private final @NotNull FindModel myFindModel;
    private final @NotNull TextSearchService myTextSearchService;

    private final boolean myHasTrigrams;
    private final String myStringToFindInIndices;

    MyFindInProjectSearcher(@NotNull Project project, @NotNull FindModel findModel) {
      myProject = project;
      myFindModel = findModel;
      myFileIndex = ProjectFileIndex.getInstance(myProject);
      myTextSearchService = TextSearchService.getInstance();
      String stringToFind = findModel.getStringToFind();

      if (findModel.isRegularExpressions()) {
        stringToFind = FindInProjectUtil.buildStringToFindForIndicesFromRegExp(stringToFind, project);
      }

      myStringToFindInIndices = stringToFind;

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
      String stringToFind = getStringToFindInIndexes(myFindModel, myProject);

      if (stringToFind.isEmpty()) {
        return Collections.emptySet();
      }


      final GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(FindInProjectUtil.getScopeFromModel(myProject, myFindModel),
                                                                                myProject);

      List<VirtualFile> hits = new ArrayList<>();
      ThrowableComputable<TextSearchService.TextSearchResult, RuntimeException> findTextComputable =
        () -> myTextSearchService.processFilesWithText(stringToFind, Processors.cancelableCollectProcessor(hits), scope);
      TextSearchService.TextSearchResult result =
        DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(findTextComputable);
      if (result != TextSearchService.TextSearchResult.NO_TRIGRAMS) {
        return Collections.unmodifiableCollection(hits);
      }

      PsiSearchHelper helper = PsiSearchHelper.getInstance(myProject);
      CacheManager cacheManager = CacheManager.getInstance(myProject);

      return DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
        Set<VirtualFile> resultFiles = new HashSet<>();

        helper.processCandidateFilesForText(scope, UsageSearchContext.ANY, myFindModel.isCaseSensitive(), stringToFind, file -> {
          ContainerUtil.addIfNotNull(resultFiles, file);
          return true;
        });

        // in case our word splitting is incorrect
        VirtualFile[] filesWithWord = cacheManager.getVirtualFilesWithWord(stringToFind, UsageSearchContext.ANY, scope,
                                                                           myFindModel.isCaseSensitive());

        Collections.addAll(resultFiles, filesWithWord);
        return Collections.unmodifiableCollection(resultFiles);
      });
    }

    @Override
    public boolean isReliable() {
      if (DumbService.isDumb(myProject)) return false;

      // a local scope may be over a non-indexed file
      if (myFindModel.getCustomScope() instanceof LocalSearchScope) return false;

      if (myHasTrigrams) return true;

      // $ is used to separate words when indexing plain-text files but not when indexing
      // Java identifiers, so we can't consistently break a string containing $ characters into words
      return myFindModel.isWholeWordsOnly() &&
             myStringToFindInIndices.indexOf('$') < 0 &&
             !StringUtil.getWordsIn(myStringToFindInIndices).isEmpty();
    }

    @Override
    public boolean isCovered(@NotNull VirtualFile file) {
      return myHasTrigrams && isCoveredByIndex(file) && (myFileIndex.isInContent(file) || myFileIndex.isInLibrary(file));
    }

    private boolean isCoveredByIndex(@NotNull VirtualFile file) {
      return myTextSearchService.isInSearchableScope(file, myProject);
    }

    private static boolean hasTrigrams(@NotNull String text) {
      return !TrigramBuilder.getTrigrams(text).isEmpty();
    }

    private static @NotNull String getStringToFindInIndexes(@NotNull FindModel findModel, @NotNull Project project) {
      String stringToFind = findModel.getStringToFind();

      if (findModel.isRegularExpressions()) {
        stringToFind = FindInProjectUtil.buildStringToFindForIndicesFromRegExp(stringToFind, project);
      }

      return stringToFind;
    }
  }
}

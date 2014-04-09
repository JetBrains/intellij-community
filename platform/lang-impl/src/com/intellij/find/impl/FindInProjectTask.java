/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageLimitUtil;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author peter
 */
class FindInProjectTask {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindInProjectTask");
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.
  private static final int SINGLE_FILE_SIZE_LIMIT = 5 * 1024 * 1024; // megabytes.
  private final FindModel myFindModel;
  private final Project myProject;
  private final PsiManager myPsiManager;
  @Nullable private final PsiDirectory myPsiDirectory;
  private final FileIndex myFileIndex;
  private final Condition<VirtualFile> myFileMask;
  private final ProgressIndicator myProgress;
  @Nullable private final Module myModule;
  private final Set<PsiFile> myLargeFiles = ContainerUtil.newTroveSet();
  private boolean myWarningShown;

  FindInProjectTask(@NotNull final FindModel findModel,
                    @NotNull final Project project,
                    @Nullable final PsiDirectory psiDirectory) {
    myFindModel = findModel;
    myProject = project;
    myPsiDirectory = psiDirectory;
    myPsiManager = PsiManager.getInstance(project);

    final String moduleName = findModel.getModuleName();
    myModule = moduleName == null ? null : ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(project).findModuleByName(moduleName);
      }
    });
    myFileIndex = myModule == null ?
                  ProjectRootManager.getInstance(project).getFileIndex() :
                  ModuleRootManager.getInstance(myModule).getFileIndex();

    final String filter = findModel.getFileFilter();
    final Pattern pattern = FindInProjectUtil.createFileMaskRegExp(filter);

    //noinspection unchecked
    myFileMask = pattern == null ? Condition.TRUE : new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        return file != null && pattern.matcher(file.getName()).matches();
      }
    };

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    myProgress = progress != null ? progress : new EmptyProgressIndicator();
  }

  public void findUsages(@NotNull final Processor<UsageInfo> consumer, @NotNull FindUsagesProcessPresentation processPresentation) {
    try {
      myProgress.setIndeterminate(true);
      myProgress.setText("Scanning indexed files...");
      final Set<PsiFile> filesForFastWordSearch = getFilesForFastWordSearch();
      myProgress.setIndeterminate(false);

      searchInFiles(consumer, processPresentation, filesForFastWordSearch);

      myProgress.setIndeterminate(true);
      myProgress.setText("Scanning non-indexed files...");
      boolean skipIndexed = canRelyOnIndices();
      final Collection<PsiFile> otherFiles = collectFilesInScope(filesForFastWordSearch, skipIndexed);
      myProgress.setIndeterminate(false);

      long start = System.currentTimeMillis();
      searchInFiles(consumer, processPresentation, otherFiles);
      if (skipIndexed && otherFiles.size() > 1000) {
        logStats(otherFiles, start);
      }
    }
    catch (ProcessCanceledException e) {
      // fine
    }

    if (!myLargeFiles.isEmpty()) {
      processPresentation.setLargeFilesWereNotScanned(myLargeFiles);
    }

    if (!myProgress.isCanceled()) {
      myProgress.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  private static void logStats(Collection<PsiFile> otherFiles, long start) {
    long time = System.currentTimeMillis() - start;

    final Multiset<String> stats = HashMultiset.create();
    for (PsiFile file : otherFiles) {
      stats.add(StringUtil.notNullize(file.getViewProvider().getVirtualFile().getExtension()).toLowerCase());
    }

    List<String> extensions = ContainerUtil.newArrayList(stats.elementSet());
    Collections.sort(extensions, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return stats.count(o2) - stats.count(o1);
      }
    });

    String message = "Search in " + otherFiles.size() + " files with unknown types took " + time + "ms.\n" +
                     "Mapping their extensions to an existing file type (e.g. Plain Text) might speed up the search.\n" +
                     "Most frequent non-indexed file extensions: ";
    for (int i = 0; i < Math.min(10, extensions.size()); i++) {
      String extension = extensions.get(i);
      message += extension + "(" + stats.count(extension) + ") ";
    }
    LOG.info(message);
  }

  private void searchInFiles(Processor<UsageInfo> consumer, FindUsagesProcessPresentation processPresentation, Collection<PsiFile> psiFiles) {
    int i = 0;
    long totalFilesSize = 0;
    int count = 0;

    for (final PsiFile psiFile : psiFiles) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      final int index = i++;
      if (virtualFile == null) continue;

      long fileLength = UsageViewManagerImpl.getFileLength(virtualFile);
      if (fileLength == -1) continue; // Binary or invalid

      if (ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile) && !Registry.is("find.search.in.project.files")) continue;

      if (fileLength > SINGLE_FILE_SIZE_LIMIT) {
        myLargeFiles.add(psiFile);
        continue;
      }

      myProgress.checkCanceled();
      myProgress.setFraction((double)index / psiFiles.size());
      String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                       myFindModel.getStringToFind(), virtualFile.getPresentableUrl());
      myProgress.setText(text);
      myProgress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", count));

      int countInFile = FindInProjectUtil.processUsagesInFile(psiFile, myFindModel, consumer);

      count += countInFile;
      if (countInFile > 0) {
        totalFilesSize += fileLength;
        if (totalFilesSize > FILES_SIZE_LIMIT && !myWarningShown) {
          myWarningShown = true;
          String message = FindBundle.message("find.excessive.total.size.prompt",
                                              UsageViewManagerImpl.presentableSize(totalFilesSize),
                                              ApplicationNamesInfo.getInstance().getProductName());
          UsageLimitUtil.showAndCancelIfAborted(myProject, message, processPresentation.getUsageViewPresentation());
        }
      }
    }
  }

  @NotNull
  private Collection<PsiFile> collectFilesInScope(@NotNull final Set<PsiFile> alreadySearched, final boolean skipIndexed) {
    SearchScope customScope = myFindModel.getCustomScope();
    final GlobalSearchScope globalCustomScope = toGlobal(customScope);

    final ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);

    class EnumContentIterator implements ContentIterator {
      final Set<PsiFile> myFiles = new LinkedHashSet<PsiFile>();

      @Override
      public boolean processFile(@NotNull final VirtualFile virtualFile) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            ProgressManager.checkCanceled();
            if (virtualFile.isDirectory() || !virtualFile.isValid() ||
                !myFileMask.value(virtualFile) ||
                (globalCustomScope != null && !globalCustomScope.contains(virtualFile))) {
              return;
            }

            if (skipIndexed && isCoveredByIdIndex(virtualFile) && 
                (fileIndex.isInContent(virtualFile) || fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile))) {
              return;
            }

            PsiFile psiFile = myPsiManager.findFile(virtualFile);
            if (psiFile != null && !(psiFile instanceof PsiBinaryFile) && !alreadySearched.contains(psiFile)) {
              PsiFile sourceFile = (PsiFile)psiFile.getNavigationElement();
              if (sourceFile != null) psiFile = sourceFile;
              myFiles.add(psiFile);
            }
          }
        });
        return true;
      }

      @NotNull
      private Collection<PsiFile> getFiles() {
        return myFiles;
      }
    }

    final EnumContentIterator iterator = new EnumContentIterator();

    if (customScope instanceof LocalSearchScope) {
      for (VirtualFile file : getLocalScopeFiles((LocalSearchScope)customScope)) {
        iterator.processFile(file);
      }
    }
    else if (myPsiDirectory != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (myPsiDirectory.isValid()) {
            addFilesUnderDirectory(myPsiDirectory, iterator);
          }
        }
      });

      myFileIndex.iterateContentUnderDirectory(myPsiDirectory.getVirtualFile(), iterator);
    }
    else {
      boolean success = myFileIndex.iterateContent(iterator);
      if (success && globalCustomScope != null && globalCustomScope.isSearchInLibraries()) {
        final VirtualFile[] librarySources = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
          @Override
          public VirtualFile[] compute() {
            OrderEnumerator enumerator = myModule == null ? OrderEnumerator.orderEntries(myProject) : OrderEnumerator.orderEntries(myModule);
            return enumerator.withoutModuleSourceEntries().withoutDepModules().getSourceRoots();
          }
        });
        iterateAll(librarySources, globalCustomScope, iterator);
      }
    }
    return iterator.getFiles();
  }

  private static boolean isCoveredByIdIndex(VirtualFile file) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    FileType fileType = file.getFileType();
    return IdIndex.isIndexable(fileType) && fileBasedIndex.isIndexingCandidate(file, IdIndex.NAME);
  }

  private static boolean iterateAll(@NotNull VirtualFile[] files, @NotNull final GlobalSearchScope searchScope, @NotNull final ContentIterator iterator) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    final VirtualFileFilter contentFilter = new VirtualFileFilter() {
      @Override
      public boolean accept(@NotNull final VirtualFile file) {
        return file.isDirectory() ||
               !fileTypeManager.isFileIgnored(file) && !file.getFileType().isBinary() && searchScope.contains(file);
      }
    };
    for (VirtualFile file : files) {
      if (!VfsUtilCore.iterateChildrenRecursively(file, contentFilter, iterator)) return false;
    }
    return true;
  }

  @Nullable
  private GlobalSearchScope toGlobal(@Nullable final SearchScope scope) {
    if (scope instanceof GlobalSearchScope || scope == null) {
      return (GlobalSearchScope)scope;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        return GlobalSearchScope.filesScope(myProject, getLocalScopeFiles((LocalSearchScope)scope));
      }
    });
  }

  @NotNull
  private static Set<VirtualFile> getLocalScopeFiles(@NotNull LocalSearchScope scope) {
    Set<VirtualFile> files = new LinkedHashSet<VirtualFile>();
    for (PsiElement element : scope.getScope()) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        ContainerUtil.addIfNotNull(files, file.getVirtualFile());
      }
    }
    return files;
  }

  private boolean canRelyOnIndices() {
    if (DumbService.isDumb(myProject)) return false;

    if (myFindModel.isRegularExpressions()) return false;

    // a local scope may be over a non-indexed file
    if (myFindModel.getCustomScope() instanceof LocalSearchScope) return false;

    String text = myFindModel.getStringToFind();
    if (StringUtil.isEmptyOrSpaces(text)) return false;

    if (TrigramIndex.ENABLED) return !TrigramBuilder.buildTrigram(text).isEmpty();

    // $ is used to separate words when indexing plain-text files but not when indexing
    // Java identifiers, so we can't consistently break a string containing $ characters into words

    return myFindModel.isWholeWordsOnly() && text.indexOf('$') < 0 && !StringUtil.getWordsInStringLongestFirst(text).isEmpty();
  }


  @NotNull
  private Set<PsiFile> getFilesForFastWordSearch() {
    String stringToFind = myFindModel.getStringToFind();
    if (stringToFind.isEmpty() || DumbService.getInstance(myProject).isDumb()) {
      return Collections.emptySet();
    }

    SearchScope customScope = myFindModel.getCustomScope();
    GlobalSearchScope scope = myPsiDirectory != null
                              ? GlobalSearchScopesCore.directoryScope(myPsiDirectory, true)
                              : myModule != null
                                ? myModule.getModuleContentScope()
                                : customScope instanceof GlobalSearchScope
                                  ? (GlobalSearchScope)customScope
                                  : toGlobal(customScope);
    if (scope == null) {
      scope = ProjectScope.getContentScope(myProject);
    }

    final Set<PsiFile> resultFiles = new LinkedHashSet<PsiFile>();

    if (TrigramIndex.ENABLED) {
      Set<Integer> keys = ContainerUtil.newTroveSet();
      TIntHashSet trigrams = TrigramBuilder.buildTrigram(stringToFind);
      TIntIterator it = trigrams.iterator();
      while (it.hasNext()) {
        keys.add(it.next());
      }

      if (!keys.isEmpty()) {
        List<VirtualFile> hits = new ArrayList<VirtualFile>();
        FileBasedIndex.getInstance().getFilesWithKey(TrigramIndex.INDEX_ID, keys, new CommonProcessors.CollectProcessor<VirtualFile>(hits), scope);

        for (VirtualFile hit : hits) {
          if (myFileMask.value(hit)) {
            resultFiles.add(findFile(hit));
          }
        }

        return resultFiles;
      }
    }

    PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(myProject);
    helper.processFilesWithText(scope, UsageSearchContext.ANY, myFindModel.isCaseSensitive(), stringToFind, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        if (myFileMask.value(file)) {
          ContainerUtil.addIfNotNull(resultFiles, findFile(file));
        }
        return true;
      }
    });

    // in case our word splitting is incorrect
    for (PsiFile file : CacheManager.SERVICE.getInstance(myProject)
      .getFilesWithWord(stringToFind, UsageSearchContext.ANY, scope, myFindModel.isCaseSensitive())) {
      if (myFileMask.value(file.getVirtualFile())) {
        resultFiles.add(file);
      }
    }

    return resultFiles;
  }

  private PsiFile findFile(@NotNull final VirtualFile virtualFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return myPsiManager.findFile(virtualFile);
      }
    });
  }

  private void addFilesUnderDirectory(@NotNull PsiDirectory directory, @NotNull ContentIterator iterator) {
    for (PsiElement child : directory.getChildren()) {
      if (child instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)child).getVirtualFile();
        if (virtualFile != null) {
          iterator.processFile(virtualFile);
        }
      }
      else if (myFindModel.isWithSubdirectories() && child instanceof PsiDirectory) {
        addFilesUnderDirectory((PsiDirectory)child, iterator);
      }
    }
  }
}

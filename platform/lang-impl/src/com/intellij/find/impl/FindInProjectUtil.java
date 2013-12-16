/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.BundleBase;
import com.intellij.find.*;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageLimitUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class FindInProjectUtil {
  private static final int USAGES_PER_READ_ACTION = 100;
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.
  private static final int SINGLE_FILE_SIZE_LIMIT = 5 * 1024 * 1024; // megabytes.

  private FindInProjectUtil() {}

  public static void setDirectoryName(@NotNull FindModel model, @NotNull DataContext dataContext) {
    PsiElement psiElement;
    try {
      psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    catch (IndexNotReadyException e) {
      psiElement = null;
    }

    String directoryName = null;

    if (psiElement instanceof PsiDirectory) {
      directoryName = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
    }

    if (directoryName == null && psiElement instanceof PsiDirectoryContainer) {
      final PsiDirectory[] directories = ((PsiDirectoryContainer)psiElement).getDirectories();
      directoryName = directories.length == 1 ? directories[0].getVirtualFile().getPresentableUrl():null;
    }

    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null) {
      model.setModuleName(module.getName());
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (model.getModuleName() == null || editor == null) {
      model.setDirectoryName(directoryName);
      model.setProjectScope(directoryName == null && module == null && !model.isCustomScope() || editor != null);

      // for convenience set directory name to directory of current file, note that we doesn't change default projectScope
      if (directoryName == null) {
        VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
        if (virtualFile != null && !virtualFile.isDirectory()) virtualFile = virtualFile.getParent();
        if (virtualFile != null) model.setDirectoryName(virtualFile.getPresentableUrl());
      }
    }
  }

  @Nullable
  public static PsiDirectory getPsiDirectory(@NotNull final FindModel findModel, @NotNull Project project) {
    String directoryName = findModel.getDirectoryName();
    if (findModel.isProjectScope() || directoryName == null) {
      return null;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    String path = directoryName.replace(File.separatorChar, '/');
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      virtualFile = null;
      for (LocalFileProvider provider : ((VirtualFileManagerEx)VirtualFileManager.getInstance()).getLocalFileProviders()) {
        VirtualFile file = provider.findLocalVirtualFileByPath(path);
        if (file != null && file.isDirectory()) {
          if (file.getChildren().length > 0) {
            virtualFile = file;
            break;
          }
          if(virtualFile == null){
             virtualFile = file;
          }
        }
      }
    }
    return virtualFile == null ? null : psiManager.findDirectory(virtualFile);
  }

  private static void addFilesUnderDirectory(@NotNull PsiDirectory directory, @NotNull Collection<PsiFile> fileList, boolean isRecursive, @Nullable Pattern fileMaskRegExp) {
    final PsiElement[] children = directory.getChildren();

    for (PsiElement child : children) {
      if (child instanceof PsiFile &&
          (fileMaskRegExp == null ||
           fileMaskRegExp.matcher(((PsiFile)child).getName()).matches()
          )
        ) {
        PsiFile file = (PsiFile)child;
        PsiFile sourceFile = (PsiFile)file.getNavigationElement();
        if (sourceFile != null) file = sourceFile;
        fileList.add(file);
      }
      else if (isRecursive && child instanceof PsiDirectory) {
        addFilesUnderDirectory((PsiDirectory)child, fileList, isRecursive, fileMaskRegExp);
      }
    }
  }

  @Nullable
  private static Pattern createFileMaskRegExp(@NotNull FindModel findModel) {
    final String filter = findModel.getFileFilter();
    return createFileMaskRegExp(filter);
  }

  @Nullable
  public static Pattern createFileMaskRegExp(@Nullable String filter) {
    if (filter == null) {
      return null;
    }
    String pattern;
    final List<String> strings = StringUtil.split(filter, ",");
    if (strings.size() == 1) {
      pattern = PatternUtil.convertToRegex(filter.trim());
    }
    else {
      pattern = StringUtil.join(strings, new Function<String, String>() {
        @NotNull
        @Override
        public String fun(@NotNull String s) {
          return "(" + PatternUtil.convertToRegex(s.trim()) + ")";
        }
      }, "|");
    }
    return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
  }

  public static void findUsages(@NotNull final FindModel findModel,
                                final PsiDirectory psiDirectory,
                                @NotNull final Project project,
                                boolean showWarnings,
                                @NotNull final Processor<UsageInfo> consumer,
                                @NotNull FindUsagesProcessPresentation processPresentation) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    final Collection<PsiFile> psiFiles = getFilesToSearchIn(findModel, project, psiDirectory);
    try {
      final Set<PsiFile> largeFiles = new THashSet<PsiFile>();

      int i = 0;
      long totalFilesSize = 0;
      int count = 0;
      final boolean[] warningShown = {false};

      for (final PsiFile psiFile : psiFiles) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final int index = i++;
        if (virtualFile == null) continue;

        long fileLength = UsageViewManagerImpl.getFileLength(virtualFile);
        if (fileLength == -1) continue; // Binary or invalid

        if (ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile) && !Registry.is("find.search.in.project.files")) continue;

        if (fileLength > SINGLE_FILE_SIZE_LIMIT) {
          largeFiles.add(psiFile);
          continue;
        }

        if (progress != null) {
          progress.checkCanceled();
          progress.setFraction((double)index / psiFiles.size());
          String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                           findModel.getStringToFind(), virtualFile.getPresentableUrl());
          progress.setText(text);
          progress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", count));
        }

        int countInFile = processUsagesInFile(psiFile, findModel, consumer);

        count += countInFile;
        if (countInFile > 0) {
          totalFilesSize += fileLength;
          if (totalFilesSize > FILES_SIZE_LIMIT && !warningShown[0]) {
            warningShown[0] = true;
            String message = FindBundle.message("find.excessive.total.size.prompt", UsageViewManagerImpl.presentableSize(totalFilesSize),
                                                ApplicationNamesInfo.getInstance().getProductName());
            UsageLimitUtil.showAndCancelIfAborted(project, message);
          }
        }
      }


      if (!largeFiles.isEmpty()) {
        processPresentation.setLargeFilesWereNotScanned(largeFiles);
      }
    }
    catch (ProcessCanceledException e) {
      // fine
    }

    if (progress != null && !progress.isCanceled()) {
      progress.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  private static int processUsagesInFile(@NotNull final PsiFile psiFile,
                                         @NotNull final FindModel findModel,
                                         @NotNull final Processor<UsageInfo> consumer) {
    if (findModel.getStringToFind().isEmpty()) {
      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
              @Override
              public Boolean compute() {
                return consumer.process(new UsageInfo(psiFile,0,0,true));
              }
            })) {
        throw new ProcessCanceledException();
      }
      return 1;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return 0;
    if (virtualFile.getFileType().isBinary()) return 0; // do not decompile .class files
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return 0;
    final int[] offset = {0};
    int count = 0;
    int found;
    ProgressIndicator indicator = ProgressWrapper.unwrap(ProgressManager.getInstance().getProgressIndicator());
    TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.getFrom(indicator);
    do {
      tooManyUsagesStatus.pauseProcessingIfTooManyUsages(); // wait for user out of read action
      found = ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
        @Override
        @NotNull
        public Integer compute() {
          if (!psiFile.isValid()) return 0;
          return addToUsages(document, consumer, findModel, psiFile, offset, USAGES_PER_READ_ACTION);
        }
      });
      count += found;
    }
    while (found != 0);
    return count;
  }

  @NotNull
  private static Collection<PsiFile> getFilesToSearchIn(@NotNull final FindModel findModel,
                                                        @NotNull final Project project,
                                                        final PsiDirectory psiDirectory) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiFile>>() {
      @Override
      public Collection<PsiFile> compute() {
        return getFilesToSearchInReadAction(findModel, project, psiDirectory);
      }
    });
  }

  @NotNull
  private static Collection<PsiFile> getFilesToSearchInReadAction(@NotNull final FindModel findModel,
                                                                  @NotNull final Project project,
                                                                  @Nullable final PsiDirectory psiDirectory) {
    String moduleName = findModel.getModuleName();
    Module module = moduleName == null ? null : ModuleManager.getInstance(project).findModuleByName(moduleName);
    final FileIndex fileIndex = module == null ?
                                ProjectRootManager.getInstance(project).getFileIndex() :
                                ModuleRootManager.getInstance(module).getFileIndex();

    if (psiDirectory == null || findModel.isWithSubdirectories() && fileIndex.isInContent(psiDirectory.getVirtualFile())) {
      final Pattern fileMaskRegExp = createFileMaskRegExp(findModel);
      // optimization
      Pair<Boolean, Collection<PsiFile>> fastWords = getFilesForFastWordSearch(findModel, project, psiDirectory, fileMaskRegExp, module, fileIndex);
      final Collection<PsiFile> filesForFastWordSearch = fastWords.getSecond();

      if (fastWords.getFirst() && canOptimizeForFastWordSearch(findModel)) return filesForFastWordSearch;

      SearchScope customScope = findModel.getCustomScope();
      final GlobalSearchScope globalCustomScope = toGlobal(project, customScope);

      class EnumContentIterator implements ContentIterator {
        final Set<PsiFile> myFiles = new LinkedHashSet<PsiFile>(filesForFastWordSearch);
        final PsiManager psiManager = PsiManager.getInstance(project);

        @Override
        public boolean processFile(@NotNull VirtualFile virtualFile) {
          ProgressManager.checkCanceled();
          if (!virtualFile.isDirectory() &&
              (fileMaskRegExp == null || fileMaskRegExp.matcher(virtualFile.getName()).matches()) &&
              (globalCustomScope == null || globalCustomScope.contains(virtualFile))) {
            final PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile != null && !filesForFastWordSearch.contains(psiFile)) {
              myFiles.add(psiFile);
            }
          }
          return true;
        }

        @NotNull
        private Collection<PsiFile> getFiles() {
          return myFiles;
        }
      }

      EnumContentIterator iterator = new EnumContentIterator();

      if (customScope instanceof LocalSearchScope) {
        for (VirtualFile file : getLocalScopeFiles((LocalSearchScope)customScope)) {
          iterator.processFile(file);
        }
      }

      if (psiDirectory == null) {
        boolean success = fileIndex.iterateContent(iterator);
        if (success && globalCustomScope != null && globalCustomScope.isSearchInLibraries()) {
          OrderEnumerator enumerator = module == null ? OrderEnumerator.orderEntries(project) : OrderEnumerator.orderEntries(module);
          final VirtualFile[] librarySources = enumerator.withoutModuleSourceEntries().withoutDepModules().getSourceRoots();
          iterateAll(librarySources, globalCustomScope, iterator);
        }
      }
      else {
        fileIndex.iterateContentUnderDirectory(psiDirectory.getVirtualFile(), iterator);
      }
      return iterator.getFiles();
    }
    if (psiDirectory.isValid()) {
      Collection<PsiFile> fileList = new THashSet<PsiFile>();
      addFilesUnderDirectory(psiDirectory, fileList, findModel.isWithSubdirectories(), createFileMaskRegExp(findModel));
      return fileList;
    }
    return Collections.emptyList();
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
  private static GlobalSearchScope toGlobal(@NotNull Project project, @Nullable SearchScope scope) {
    if (scope instanceof GlobalSearchScope || scope == null) {
      return (GlobalSearchScope)scope;
    }
    return GlobalSearchScope.filesScope(project, getLocalScopeFiles((LocalSearchScope)scope));
  }

  private static Set<VirtualFile> getLocalScopeFiles(LocalSearchScope scope) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    for (PsiElement element : scope.getScope()) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        ContainerUtil.addIfNotNull(files, file.getVirtualFile());
      }
    }
    return files;
  }

  @NotNull
  private static Pair<Boolean, Collection<PsiFile>> getFilesForFastWordSearch(@NotNull final FindModel findModel,
                                                                              @NotNull final Project project,
                                                                              @Nullable final PsiDirectory psiDirectory,
                                                                              final Pattern fileMaskRegExp,
                                                                              @Nullable final Module module, FileIndex fileIndex) {
    if (DumbService.getInstance(project).isDumb()) {
      return new Pair<Boolean, Collection<PsiFile>>(false, Collections.<PsiFile>emptyList());
    }

    PsiManager pm = PsiManager.getInstance(project);
    CacheManager cacheManager = CacheManager.SERVICE.getInstance(project);
    SearchScope customScope = findModel.getCustomScope();
    GlobalSearchScope scope = psiDirectory != null
                              ? GlobalSearchScopes.directoryScope(psiDirectory, true)
                              : module != null
                                ? module.getModuleContentScope()
                                : customScope instanceof GlobalSearchScope
                                  ? (GlobalSearchScope)customScope
                                  : toGlobal(project, customScope);
    if (scope == null) {
      scope = ProjectScope.getContentScope(project);
    }

    Set<Integer> keys = new THashSet<Integer>(30);
    final Set<PsiFile> resultFiles = new THashSet<PsiFile>();
    boolean fast = false;

    String stringToFind = findModel.getStringToFind();
    if (TrigramIndex.ENABLED) {
      TIntHashSet trigrams = TrigramBuilder.buildTrigram(stringToFind);
      TIntIterator it = trigrams.iterator();
      while (it.hasNext()) {
        keys.add(it.next());
      }

      if (!keys.isEmpty()) {
        fast = true;
        List<VirtualFile> hits = new ArrayList<VirtualFile>();
        FileBasedIndex.getInstance().getFilesWithKey(TrigramIndex.INDEX_ID, keys, new CommonProcessors.CollectProcessor<VirtualFile>(hits), scope);

        for (VirtualFile hit : hits) {
          resultFiles.add(pm.findFile(hit));
        }

        filterMaskedFiles(resultFiles, fileMaskRegExp);
        if (resultFiles.isEmpty()) return new Pair<Boolean, Collection<PsiFile>>(true, resultFiles);
      }
    }


    // $ is used to separate words when indexing plain-text files but not when indexing
    // Java identifiers, so we can't consistently break a string containing $ characters into words

    fast |= findModel.isWholeWordsOnly() && stringToFind.indexOf('$') < 0;

    List<String> words = StringUtil.getWordsInStringLongestFirst(stringToFind);

    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);

      PsiFile[] files = cacheManager.getFilesWithWord(word, UsageSearchContext.ANY, scope, findModel.isCaseSensitive());
      if (files.length == 0) {
        resultFiles.clear();
        break;
      }

      final List<PsiFile> psiFiles = Arrays.asList(files);

      if (i == 0 && keys.isEmpty()) {
        resultFiles.addAll(psiFiles);
      }
      else {
        resultFiles.retainAll(psiFiles);
      }

      filterMaskedFiles(resultFiles, fileMaskRegExp);
      if (resultFiles.isEmpty()) break;
    }

    if (stringToFind.isEmpty()) {
      fileIndex.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile file) {
          if (!file.isDirectory() && fileMaskRegExp.matcher(file.getName()).matches()) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
              resultFiles.add(psiFile);
            }
          }
          return true;
        }
      });
    }
    else {
      // in case our word splitting is incorrect
      PsiFile[] allWordsFiles =
        cacheManager.getFilesWithWord(stringToFind, UsageSearchContext.ANY, scope, findModel.isCaseSensitive());
      ContainerUtil.addAll(resultFiles, allWordsFiles);

      filterMaskedFiles(resultFiles, fileMaskRegExp);
    }

    return new Pair<Boolean, Collection<PsiFile>>(fast, resultFiles);
  }

  private static void filterMaskedFiles(@NotNull final Set<PsiFile> resultFiles, @Nullable final Pattern fileMaskRegExp) {
    if (fileMaskRegExp != null) {
      for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext();) {
        PsiFile file = iterator.next();
        if (!fileMaskRegExp.matcher(file.getName()).matches()) {
          iterator.remove();
        }
      }
    }
  }

  private static boolean canOptimizeForFastWordSearch(@NotNull final FindModel findModel) {
    return !findModel.isRegularExpressions()
           && (findModel.getCustomScope() == null || findModel.getCustomScope() instanceof GlobalSearchScope);
  }

  private static int addToUsages(@NotNull Document document, @NotNull Processor<UsageInfo> consumer, @NotNull FindModel findModel,
                                 @NotNull final PsiFile psiFile, int[] offsetRef, int maxUsages) {
    int count = 0;
    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    int offset = offsetRef[0];

    Project project = psiFile.getProject();

    FindManager findManager = FindManager.getInstance(project);
    while (offset < textLength) {
      FindResult result = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!result.isStringFound()) break;

      final SearchScope customScope = findModel.getCustomScope();
      if (customScope instanceof LocalSearchScope) {
        final TextRange range = new TextRange(result.getStartOffset(), result.getEndOffset());
        if (!((LocalSearchScope)customScope).containsRange(psiFile, range)) break;
      }
      UsageInfo info = new FindResultUsageInfo(findManager, psiFile, offset, findModel, result);
      if (!consumer.process(info)){
        throw new ProcessCanceledException();
      }
      count++;

      final int prevOffset = offset;
      offset = result.getEndOffset();

      if (prevOffset == offset) {
        // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
        ++offset;
      }
      if (maxUsages > 0 && count >= maxUsages) {
        break;
      }
    }
    offsetRef[0] = offset;
    return count;
  }

  private static String getTitleForScope(@NotNull final FindModel findModel) {
    String result;

    if (findModel.isProjectScope()) {
      result = FindBundle.message("find.scope.project.title");
    }
    else if (findModel.getModuleName() != null) {
      result = FindBundle.message("find.scope.module.title", findModel.getModuleName());
    }
    else if(findModel.getCustomScopeName() != null) {
      result = findModel.getCustomScopeName();
    }
    else {
      result = FindBundle.message("find.scope.directory.title", findModel.getDirectoryName());
    }

    if (findModel.getFileFilter() != null) {
      result = FindBundle.message("find.scope.files.with.mask", result, findModel.getFileFilter());
    }

    return result;
  }

  @NotNull
  public static UsageViewPresentation setupViewPresentation(final boolean toOpenInNewTab, @NotNull final FindModel findModelCopy) {
    final UsageViewPresentation presentation = new UsageViewPresentation();

    final String scope = getTitleForScope(findModelCopy);
    final String stringToFind = findModelCopy.getStringToFind();
    presentation.setScopeText(scope);
    if (stringToFind.isEmpty()) {
      presentation.setTabText("Files");
      presentation.setToolwindowTitle(BundleBase.format("Files in ''{0}''", scope));
      presentation.setUsagesString("files");
    }
    else {
      presentation.setTabText(FindBundle.message("find.usage.view.tab.text", stringToFind));
      presentation.setToolwindowTitle(FindBundle.message("find.usage.view.toolwindow.title", stringToFind, scope));
      presentation.setUsagesString(FindBundle.message("find.usage.view.usages.text", stringToFind));
    }
    presentation.setOpenInNewTab(toOpenInNewTab);
    presentation.setCodeUsages(false);

    return presentation;
  }

  @NotNull
  public static FindUsagesProcessPresentation setupProcessPresentation(final Project project,
                                                                       final boolean showPanelIfOnlyOneUsage,
                                                                       @NotNull final UsageViewPresentation presentation) {
    FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowFindOptionsPrompt(false);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);
    processPresentation.setProgressIndicatorFactory(
      new Factory<ProgressIndicator>() {
        @NotNull
        @Override
        public ProgressIndicator create() {
          return new FindProgressIndicator(project, presentation.getScopeText());
        }
      }
    );
    return processPresentation;
  }

  public static class StringUsageTarget implements UsageTarget {
    private final String myStringToFind;

    private final ItemPresentation myItemPresentation = new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return FindBundle.message("find.usage.target.string.text", myStringToFind);
      }

      @Override
      public String getLocationString() {
        return myStringToFind + "!!";
      }

      @Override
      public Icon getIcon(boolean open) {
        return null;
      }
    };

    public StringUsageTarget(@NotNull String _stringToFind) {
      myStringToFind = _stringToFind;
    }

    @Override
    public void findUsages() {}
    @Override
    public void findUsagesInEditor(@NotNull FileEditor editor) {}
    @Override
    public void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {}

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }

    @Override
    @Nullable
    public VirtualFile[] getFiles() {
      return null;
    }

    @Override
    public void update() {
    }

    @Override
    public String getName() {
      return myStringToFind;
    }

    @Override
    public ItemPresentation getPresentation() {
      return myItemPresentation;
    }

    @Override
    public void navigate(boolean requestFocus) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }
}

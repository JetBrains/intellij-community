/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.find.*;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.ui.GuiUtils;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
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
  private static final int USAGES_LIMIT = 1000;
  private static final int USAGES_PER_READ_ACTION = 100;
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.
  private static final int SINGLE_FILE_SIZE_LIMIT = 5 * 1024 * 1024; // megabytes.

  private FindInProjectUtil() {}

  public static void setDirectoryName(FindModel model, DataContext dataContext) {
    PsiElement psiElement;
    try {
      psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    catch (IndexNotReadyException e) {
      psiElement = null;
    }

    String directoryName = null;

    if (psiElement instanceof PsiDirectory) {
      directoryName = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
    }
    else {
      final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
      if (psiFile != null) {
        PsiDirectory psiDirectory = psiFile.getContainingDirectory();
        if (psiDirectory != null) {
          directoryName = psiDirectory.getVirtualFile().getPresentableUrl();
        }
      }
    }

    if (directoryName == null && psiElement instanceof PsiDirectoryContainer) {
      final PsiDirectory[] directories = ((PsiDirectoryContainer)psiElement).getDirectories();
      directoryName = directories.length == 1 ? directories[0].getVirtualFile().getPresentableUrl():null;
    }

    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null) {
      model.setModuleName(module.getName());
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (model.getModuleName() == null || editor == null) {
      model.setDirectoryName(directoryName);
      model.setProjectScope(directoryName == null && module == null && !model.isCustomScope() || editor != null);
    }
  }

  @Nullable
  public static PsiDirectory getPsiDirectory(final FindModel findModel, Project project) {
    String directoryName = findModel.getDirectoryName();
    if (findModel.isProjectScope() || directoryName == null) {
      return null;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    String path = directoryName.replace(File.separatorChar, '/');
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      if (!path.contains(JarFileSystem.JAR_SEPARATOR)) {
        path += JarFileSystem.JAR_SEPARATOR;
      }
      virtualFile = JarFileSystem.getInstance().findFileByPath(path);
    }
    return virtualFile == null ? null : psiManager.findDirectory(virtualFile);
  }

  private static void addFilesUnderDirectory(PsiDirectory directory, Collection<PsiFile> fileList, boolean isRecursive, Pattern fileMaskRegExp) {
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

  @NotNull
  public static List<UsageInfo> findUsages(final FindModel findModel, final PsiDirectory psiDirectory, final Project project) {

    final CommonProcessors.CollectProcessor<UsageInfo> collector = new CommonProcessors.CollectProcessor<UsageInfo>();
    findUsages(findModel, psiDirectory, project, collector);

    return new ArrayList<UsageInfo>(collector.getResults());
  }

  @Nullable
  private static Pattern createFileMaskRegExp(FindModel findModel) {
    final String filter = findModel.getFileFilter();
    return createFileMaskRegExp(filter);
  }

  public static Pattern createFileMaskRegExp(String filter) {
    if (filter == null) {
      return null;
    }
    String pattern;
    final String[] strings = filter.split(",");
    if (strings.length == 1) {
      pattern = PatternUtil.convertToRegex(filter.trim());
    }
    else {
      pattern = StringUtil.join(strings, new Function<String, String>() {
        public String fun(String s) {
          return "(" + PatternUtil.convertToRegex(s.trim()) + ")";
        }
      }, "|");
    }
    return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
  }

  public static void findUsages(final FindModel findModel,
                                final PsiDirectory psiDirectory,
                                final Project project,
                                final Processor<UsageInfo> consumer) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    final Collection<PsiFile> psiFiles = getFilesToSearchIn(findModel, project, psiDirectory);
    try {
      final SearchScope customScope = findModel.getCustomScope();
      final Set<PsiFile> largeFiles = new THashSet<PsiFile>();

      int i = 0;
      long totalFilesSize = 0;
      int count = 0;
      final boolean[] warningShown = new boolean[] {false};

      final UsageViewManager usageViewManager = UsageViewManager.getInstance(project);
      for (final PsiFile psiFile : psiFiles) {
        usageViewManager.checkSearchCanceled();
        if (customScope != null && !ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            return PsiSearchScopeUtil.isInScope(customScope, psiFile);
          }
        })) {
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final int index = i++;
        if (virtualFile == null) continue;

        long fileLength = getFileLength(virtualFile);
        if (fileLength == -1) continue; // Binary or invalid

        if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) continue;

        if (fileLength > SINGLE_FILE_SIZE_LIMIT) {
          largeFiles.add(psiFile);
          continue;
        }

        if (progress != null) {
          progress.setFraction((double)index / psiFiles.size());
          String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                           findModel.getStringToFind(), virtualFile.getPresentableUrl());
          progress.setText(text);
          progress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", count));
        }

        int countInFile = processUsagesInFile(psiFile, findModel, consumer, count, warningShown);

        if (countInFile > 0) {
          totalFilesSize += fileLength;
          if (totalFilesSize > FILES_SIZE_LIMIT && !warningShown[0]) {
            showTooManyUsagesWaring(project, FindBundle.message("find.excessive.total.size.prompt", presentableSize(totalFilesSize),
                                                                ApplicationNamesInfo.getInstance().getProductName()));
            warningShown[0] = true;
          }
        }
      }

      if (!largeFiles.isEmpty()) {
        final StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        if (largeFiles.size() == 1) {
          final VirtualFile vFile = largeFiles.iterator().next().getVirtualFile();
          message.
            append("File '").
            append(getPresentablePath(vFile)).
            append("'&nbsp;(").
            append(presentableSize(getFileLength(vFile))).
            append(") is ");
        }
        else {
          message.append("Files<br> ");

          int counter = 0;
          for (PsiFile file : largeFiles) {
            final VirtualFile vFile = file.getVirtualFile();
            message.
              append(getPresentablePath(vFile)).
              append("&nbsp;(").
              append(presentableSize(getFileLength(vFile))).
              append(")<br> ");
            if (counter++ > 10) break;
          }

          message.append("are ");
        }

        message.append("too large and cannot be scanned</body></html>");

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(project).notifyByBalloon(ToolWindowId.FIND, MessageType.WARNING, message.toString());
          }
        });
      }
    }
    catch (ProcessCanceledException e) {
      // fine
    }

    if (progress != null) {
      progress.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  private static int processUsagesInFile(final PsiFile psiFile,
                                         final FindModel findModel,
                                         final Processor<UsageInfo> consumer,
                                         int alreadyCounted,
                                         boolean[] warningShown) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return 0;
    if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return 0; // do not decompile .class files
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return 0;
    final int[] offset = new int[] {0};
    int count = 0;
    int found;
    do {
      found = ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
        @Override
        @NotNull
        public Integer compute() {
          if (!psiFile.isValid()) return 0;
          return addToUsages(document, consumer, findModel, psiFile, offset, USAGES_PER_READ_ACTION);
        }
      });
      count += found;
      if (found > 0 && count + alreadyCounted > USAGES_LIMIT && !warningShown[0]) {
        showTooManyUsagesWaring(psiFile.getProject(), FindBundle.message("find.excessive.usage.count.prompt", count));
        warningShown[0] = true;
      }

    }
    while (found != 0);
    return count;
  }

  private static String getPresentablePath(final VirtualFile virtualFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return virtualFile.getPresentableUrl();
      }
    });
  }

  private static String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return FindBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  private static long getFileLength(final VirtualFile virtualFile) {
    final long[] length = {-1L};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (!virtualFile.isValid()) return;
        if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return;
        length[0] = virtualFile.getLength();
      }
    });
    return length[0];
  }

  private static void showTooManyUsagesWaring(final Project project, final String message) {
    int retCode = invokeAndWait(new Computable<Integer>() {
      public Integer compute() {
        return Messages.showYesNoDialog(project, message, FindBundle.message("find.excessive.usages.title"), Messages.getWarningIcon());
      }
    });

    if (retCode != DialogWrapper.OK_EXIT_CODE) {
      throw new ProcessCanceledException();
    }
  }

  private static int invokeAndWait(final Computable<Integer> f) {
    final int[] answer = new int[1];
    try {
      GuiUtils.invokeAndWait(new Runnable() {
        public void run() {
          answer[0] = f.compute();
        }
      });
    }
    catch (Exception e) {
      answer[0] = 0;
    }

    return answer[0];
  }

  private static int showMessage(final Project project, final String message, final String title, final String[] buttons) {
    return invokeAndWait(new Computable<Integer>() {
      public Integer compute() {
        return Messages.showDialog(project, message, title, buttons, 0, Messages.getWarningIcon());
      }
    });
  }

  private static Collection<PsiFile> getFilesToSearchIn(final FindModel findModel, final Project project, final PsiDirectory psiDirectory) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiFile>>() {
      public Collection<PsiFile> compute() {
        return getFilesToSearchInReadAction(findModel, project, psiDirectory);
      }
    });
  }
  private static Collection<PsiFile> getFilesToSearchInReadAction(final FindModel findModel, final Project project, final PsiDirectory psiDirectory) {
    String moduleName = findModel.getModuleName();
    Module module = moduleName == null ? null : ModuleManager.getInstance(project).findModuleByName(moduleName);
    final FileIndex fileIndex = module == null ?
                                ProjectRootManager.getInstance(project).getFileIndex() :
                                ModuleRootManager.getInstance(module).getFileIndex();

    if (psiDirectory == null || findModel.isWithSubdirectories() && fileIndex.isInContent(psiDirectory.getVirtualFile())) {
      final Pattern fileMaskRegExp = createFileMaskRegExp(findModel);
      // optimization
      Pair<Boolean, Collection<PsiFile>> fastWords = getFilesForFastWordSearch(findModel, project, psiDirectory, fileMaskRegExp, module);
      final Collection<PsiFile> filesForFastWordSearch = fastWords.getSecond();

      if (fastWords.getFirst() && canOptimizeForFastWordSearch(findModel)) return filesForFastWordSearch;

      class EnumContentIterator implements ContentIterator {
        final List<PsiFile> myFiles = new ArrayList<PsiFile>(filesForFastWordSearch);
        final PsiManager psiManager = PsiManager.getInstance(project);

        public boolean processFile(VirtualFile virtualFile) {
          if (!virtualFile.isDirectory() && (fileMaskRegExp == null || fileMaskRegExp.matcher(virtualFile.getName()).matches()) ) {
            final PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile != null && !filesForFastWordSearch.contains(psiFile)) {
              myFiles.add(psiFile);
            }
          }
          return true;
        }

        private Collection<PsiFile> getFiles() {
          return myFiles;
        }
      }
      final EnumContentIterator iterator = new EnumContentIterator();

      if (psiDirectory == null) {
        boolean success = fileIndex.iterateContent(iterator);
        SearchScope customScope = findModel.getCustomScope();
        if (success && customScope instanceof GlobalSearchScope && ((GlobalSearchScope)customScope).isSearchInLibraries()) {
          OrderEnumerator enumerator = module == null ? OrderEnumerator.orderEntries(project) : OrderEnumerator.orderEntries(module);
          final VirtualFile[] librarySources = enumerator.withoutModuleSourceEntries().withoutDepModules().getSourceRoots();
          iterateAll(librarySources, (GlobalSearchScope)customScope, iterator);
        }
      }
      else {
        fileIndex.iterateContentUnderDirectory(psiDirectory.getVirtualFile(), iterator);
      }
      return iterator.getFiles();
    }
    else {
      Collection<PsiFile> fileList = new THashSet<PsiFile>();

      addFilesUnderDirectory(psiDirectory,
                             fileList,
                             findModel.isWithSubdirectories(),
                             createFileMaskRegExp(findModel));
      return fileList;
    }
  }

  private static boolean iterateAll(VirtualFile[] files, final GlobalSearchScope searchScope, final ContentIterator iterator) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    final VirtualFileFilter contentFilter = new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        if (file.isDirectory()) return true;
        if (fileTypeManager.isFileIgnored(file.getName()) || fileTypeManager.getFileTypeByFile(file).isBinary()) return false;
        return searchScope.contains(file);
      }
    };
    for (VirtualFile file : files) {
      if (!FileIndexImplUtil.iterateRecursively(file, contentFilter, iterator)) return false;
    }
    return true;
  }

  @NotNull
  private static Pair<Boolean, Collection<PsiFile>> getFilesForFastWordSearch(final FindModel findModel, final Project project,
                                                               final PsiDirectory psiDirectory, final Pattern fileMaskRegExp,
                                                               final Module module) {
    if (DumbService.getInstance(project).isDumb()) {
      return new Pair<Boolean, Collection<PsiFile>>(false, Collections.<PsiFile>emptyList());
    }

    PsiManager pm = PsiManager.getInstance(project);
    CacheManager cacheManager = ((PsiManagerEx)pm).getCacheManager();
    SearchScope customScope = findModel.getCustomScope();
    @NotNull GlobalSearchScope scope = psiDirectory != null
                                       ? GlobalSearchScope.directoryScope(psiDirectory, true)
                                       : module != null
                                         ? moduleContentScope(module)
                                         : customScope instanceof GlobalSearchScope
                                           ? (GlobalSearchScope)customScope
                                           : GlobalSearchScope.projectScope(project);

    Set<Integer> keys = new THashSet<Integer>(30);
    Set<PsiFile> resultFiles = new THashSet<PsiFile>();
    boolean fast = false;

    if (TrigramIndex.ENABLED) {
      TIntHashSet trigrams = TrigramBuilder.buildTrigram(findModel.getStringToFind());
      TIntIterator it = trigrams.iterator();
      while (it.hasNext()) {
        keys.add(it.next());
      }

      if (!keys.isEmpty()) {
        fast = true;
        List<VirtualFile> hits = new ArrayList<VirtualFile>();
        FileBasedIndex.getInstance()
          .getFilesWithKey(TrigramIndex.INDEX_ID, keys, new CommonProcessors.CollectProcessor<VirtualFile>(hits), scope);

        for (VirtualFile hit : hits) {
          resultFiles.add(pm.findFile(hit));
        }

        filterMaskedFiles(resultFiles, fileMaskRegExp);
        if (resultFiles.isEmpty()) return new Pair<Boolean, Collection<PsiFile>>(true, resultFiles);
      }
    }


    // $ is used to separate words when indexing plain-text files but not when indexing
    // Java identifiers, so we can't consistently break a string containing $ characters into words

    fast |= findModel.isWholeWordsOnly() && findModel.getStringToFind().indexOf('$') < 0;

    List<String> words = StringUtil.getWordsIn(findModel.getStringToFind());

    // hope long words are rare
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });

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

    // in case our word splitting is incorrect
    PsiFile[] allWordsFiles =
      cacheManager.getFilesWithWord(findModel.getStringToFind(), UsageSearchContext.ANY, scope, findModel.isCaseSensitive());
    ContainerUtil.addAll(resultFiles, allWordsFiles);

    filterMaskedFiles(resultFiles, fileMaskRegExp);

    return new Pair<Boolean, Collection<PsiFile>>(fast, resultFiles);
  }

  private static GlobalSearchScope moduleContentScope(final Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    GlobalSearchScope result = null;
    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    for (VirtualFile root : contentRoots) {
      PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        GlobalSearchScope moduleContent = GlobalSearchScope.directoryScope(directory, true);
        result = result == null ? moduleContent : result.uniteWith(moduleContent);
      }
    }
    if (result == null) {
      result = GlobalSearchScope.EMPTY_SCOPE;
    }
    return result;
  }

  private static void filterMaskedFiles(final Set<PsiFile> resultFiles, final Pattern fileMaskRegExp) {
    if (fileMaskRegExp != null) {
      for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext();) {
        PsiFile file = iterator.next();
        if (!fileMaskRegExp.matcher(file.getName()).matches()) {
          iterator.remove();
        }
      }
    }
  }

  private static boolean canOptimizeForFastWordSearch(final FindModel findModel) {
    return !findModel.isRegularExpressions()
           && (findModel.getCustomScope() == null || findModel.getCustomScope() instanceof GlobalSearchScope)
      ;
  }

  private static int addToUsages(@NotNull Document document, @NotNull Processor<UsageInfo> consumer, @NotNull FindModel findModel,
                                 @NotNull final PsiFile psiFile, int[] offsetRef, int maxUsages) {
    int count = 0;
    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    int offset = offsetRef[0];

    Project project = psiFile.getProject();

    UsageViewManager usageViewManager = UsageViewManager.getInstance(project);
    FindManager findManager = FindManager.getInstance(project);
    while (offset < textLength) {
      usageViewManager.checkSearchCanceled();
      FindResult result = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!result.isStringFound()) break;

      UsageInfo info = new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset());
      consumer.process(info);
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

  private static String getTitleForScope(final FindModel findModel) {
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

  public static UsageViewPresentation setupViewPresentation(final boolean toOpenInNewTab, final FindModel findModelCopy) {
    final UsageViewPresentation presentation = new UsageViewPresentation();

    final String scope = getTitleForScope(findModelCopy);
    final String stringToFind = findModelCopy.getStringToFind();
    presentation.setScopeText(scope);
    presentation.setTabText(FindBundle.message("find.usage.view.tab.text", stringToFind));
    presentation.setToolwindowTitle(FindBundle.message("find.usage.view.toolwindow.title", stringToFind, scope));
    presentation.setUsagesString(FindBundle.message("find.usage.view.usages.text", stringToFind));
    presentation.setOpenInNewTab(toOpenInNewTab);
    presentation.setCodeUsages(false);

    return presentation;
  }

  public static boolean hasReadOnlyUsages(final Collection<Usage> usages) {
    for (Usage usage : usages) {
      if (usage.isReadOnly()) return true;
    }

    return false;
  }

  public static FindUsagesProcessPresentation setupProcessPresentation(final Project project,
                                                                       final boolean showPanelIfOnlyOneUsage,
                                                                       final UsageViewPresentation presentation) {
    FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);
    processPresentation.setProgressIndicatorFactory(
      new Factory<ProgressIndicator>() {
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
      public String getPresentableText() {
        return FindBundle.message("find.usage.target.string.text", myStringToFind);
      }

      public String getLocationString() {
        return myStringToFind + "!!";
      }

      public Icon getIcon(boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };

    public StringUsageTarget(String _stringToFind) {
      myStringToFind = _stringToFind;
    }

    public void findUsages() {}
    public void findUsagesInEditor(@NotNull FileEditor editor) {}
    public void highlightUsages(PsiFile file, Editor editor, boolean clearHighlights) {}

    public boolean isValid() {
      return true;
    }

    public boolean isReadOnly() {
      return true;
    }

    @Nullable
    public VirtualFile[] getFiles() {
      return null;
    }

    public void update() {
    }

    public String getName() {
      return myStringToFind;
    }

    public ItemPresentation getPresentation() {
      return myItemPresentation;
    }

    public FileStatus getFileStatus() {
      return FileStatus.NOT_CHANGED;
    }

    public void navigate(boolean requestFocus) {
      throw new UnsupportedOperationException();
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }
  }
}

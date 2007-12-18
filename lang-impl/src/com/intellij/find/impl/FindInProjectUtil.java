package com.intellij.find.impl;

import com.intellij.find.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.usageView.AsyncFindUsagesProcessListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class FindInProjectUtil {
  private static final int USAGES_LIMIT = 1000;
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.
  private static final int SINGLE_FILE_SIZE_LIMIT = 5 * 1024 * 1024; // megabytes.

  private static final int SKIP = 0;
  private static final int PROCESS_FILE = 1;
  private static final int SKIP_ALL = 2;
  private static final int PROCESS_ALL = 3;

  private FindInProjectUtil() {}

  public static void setDirectoryName(FindModel model, DataContext dataContext) {
    PsiElement psiElement = DataKeys.PSI_ELEMENT.getData(dataContext);

    String directoryName = null;

    if (psiElement instanceof PsiDirectory) {
      directoryName = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
    }
    else {
      final PsiFile psiFile = DataKeys.PSI_FILE.getData(dataContext);
      if (psiFile != null) {
        PsiDirectory psiDirectory = psiFile.getContainingDirectory();
        if (psiDirectory != null) {
          directoryName = psiDirectory.getVirtualFile().getPresentableUrl();
        }
      }
    }

    if (directoryName == null && psiElement instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)psiElement).getDirectories();
      directoryName = directories.length == 1 ? directories[0].getVirtualFile().getPresentableUrl():null;
    }

    Module module = DataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null) {
      model.setModuleName(module.getName());
    }

    if (model.getModuleName() == null || dataContext.getData(DataConstants.EDITOR) == null) {
      model.setDirectoryName(directoryName);
      model.setProjectScope(directoryName == null && module == null && model.getCustomScopeName() == null || dataContext.getData(DataConstants.EDITOR) != null);
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
    if (virtualFile != null) {
      return psiManager.findDirectory(virtualFile);
    }
    else {
      return null;
    }
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
    class MyAsyncUsageConsumer implements AsyncFindUsagesProcessListener {
      final ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();

      public void foundUsage(UsageInfo info) {
        usages.add(info);
      }

      public int getCount() {
        return usages.size();
      }

      public void findUsagesCompleted() {
      }
    }

    MyAsyncUsageConsumer consumer = new MyAsyncUsageConsumer();
    findUsages(findModel, psiDirectory, project, consumer);
    return consumer.usages;
  }

  @Nullable
  private static Pattern createFileMaskRegExp(FindModel findModel) {
    if (findModel.getFileFilter() != null) {
      return Pattern.compile(PatternUtil.convertToRegex(findModel.getFileFilter()), Pattern.CASE_INSENSITIVE);
    }
    else {
      return null;
    }
  }

  public static void findUsages(final FindModel findModel,
                                final PsiDirectory psiDirectory,
                                final Project project,
                                final AsyncFindUsagesProcessListener consumer) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    final Collection<PsiFile> psiFiles = getFilesToSearchIn(findModel, project, psiDirectory);
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    try {
      final SearchScope customScope = findModel.getCustomScope();
      
      int i = 0;
      long totalFilesSize = 0;
      final int[] count = new int[]{0};
      boolean warningShown = false;

      boolean skipAllLarge = false;
      boolean processAllLarge = false;

      for (final PsiFile psiFile : psiFiles) {
        if (UsageViewManager.getInstance(project).searchHasBeenCancelled()) break;
        ProgressManager.getInstance().checkCanceled();
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

        if (fileLength > SINGLE_FILE_SIZE_LIMIT) {
          if (skipAllLarge) continue;
          if (!processAllLarge) {
            int retCode = showMessage(project, FindBundle.message("find.skip.large.file.prompt",
                                                                  ApplicationNamesInfo.getInstance().getProductName(),
                                                                  getPresentablePath(virtualFile), presentableSize(fileLength)),
                                      FindBundle.message("find.skip.large.file.title"),
                                      new String[] {
                                        FindBundle.message("find.skip.large.file.skip.file"),
                                        FindBundle.message("find.skip.large.file.scan.file"),
                                        FindBundle.message("find.skip.large.file.skip.all"),
                                        FindBundle.message("find.skip.large.file.scan.all")
                                      });
            if (retCode == SKIP_ALL) {
              skipAllLarge = true;
              continue;
            }
            else if (retCode == SKIP) {
              continue;
            }
            else if (retCode == PROCESS_ALL) {
              processAllLarge = true;
            }
            else {
              if (retCode == -1) retCode = PROCESS_FILE; //ESC pressed
              assert retCode == PROCESS_FILE : retCode;
            }
          }
        }

        int countBefore = count[0];

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (virtualFile.isValid()) {
              // Check once more if valid and text since we're in new read action and things might have been changed.
              if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return; // do not decompile .class files
              final Document document = manager.getDocument(virtualFile);
              if (document != null) {
                addToUsages(project, document, consumer, findModel, psiFile, count);

                if (progress != null) {
                  progress.setFraction((double)index / psiFiles.size());
                  String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                                   findModel.getStringToFind(), virtualFile.getPresentableUrl());
                  progress.setText(text);
                  int size = consumer.getCount();
                  progress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", size));
                }
              }
            }
          }
        });

        if (countBefore < count[0]) {
          totalFilesSize += fileLength;
          if (totalFilesSize > FILES_SIZE_LIMIT && !warningShown) {
            showTooManyUsagesWaring(project, FindBundle.message("find.excessive.total.size.prompt", presentableSize(totalFilesSize),
                                                                ApplicationNamesInfo.getInstance().getProductName()));
            warningShown = true;
          }
        }

        if (count[0] > USAGES_LIMIT && !warningShown) {
          showTooManyUsagesWaring(project, FindBundle.message("find.excessive.usage.count.prompt", count[0]));
          warningShown = true;
        }
      }
    }
    catch (ProcessCanceledException e) {
      // fine
    }

    if (progress != null) {
      progress.setText(FindBundle.message("find.progress.search.completed"));
    }

    consumer.findUsagesCompleted();
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
    final long[] length = new long[] {-1L};
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
      SwingUtilities.invokeAndWait(new Runnable() {
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
      final Collection<PsiFile> filesForFastWordSearch = getFilesForFastWordSearch(findModel, project, psiDirectory, fileMaskRegExp, module);
      if (filesForFastWordSearch != null && canOptimizeForFastWordSearch(findModel)) return filesForFastWordSearch;

      class EnumContentIterator implements ContentIterator {
        final List<PsiFile> myFiles = new ArrayList<PsiFile>(filesForFastWordSearch == null ? Collections.<PsiFile>emptyList() : filesForFastWordSearch);
        final PsiManager psiManager = PsiManager.getInstance(project);

        public boolean processFile(VirtualFile virtualFile) {
          if (!virtualFile.isDirectory() && (fileMaskRegExp == null || fileMaskRegExp.matcher(virtualFile.getName()).matches()) ) {
            final PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile != null && (filesForFastWordSearch == null || !filesForFastWordSearch.contains(psiFile))) {
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
          final Collection<VirtualFile> librarySources = new THashSet<VirtualFile>();
          Module[] modules = module == null ? ModuleManager.getInstance(project).getModules() : new Module[]{module};
          for (Module mod : modules) {
            ModuleRootManager.getInstance(mod).processOrder(new RootPolicy<Object>(){
              public Object visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final Object value) {
                VirtualFile[] sources = libraryOrderEntry.getFiles(OrderRootType.SOURCES);
                librarySources.addAll(Arrays.asList(sources));
                return null;
              }

              public Object visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final Object value) {
                VirtualFile[] sources = jdkOrderEntry.getFiles(OrderRootType.SOURCES);
                librarySources.addAll(Arrays.asList(sources));
                return null;
              }
            }, null);
          }
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

  private static boolean iterateAll(Collection<VirtualFile> files, final GlobalSearchScope searchScope, final ContentIterator iterator) {
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

  @Nullable
  private static Collection<PsiFile> getFilesForFastWordSearch(final FindModel findModel, final Project project,
                                                               final PsiDirectory psiDirectory, final Pattern fileMaskRegExp,
                                                               final Module module) {
    CacheManager cacheManager = ((PsiManagerEx)PsiManager.getInstance(project)).getCacheManager();
    SearchScope customScope = findModel.getCustomScope();
    @NotNull GlobalSearchScope scope = psiDirectory != null
                              ? GlobalSearchScope.directoryScope(psiDirectory, true)
                              : module != null ? moduleContentScope(module)
                              : customScope instanceof GlobalSearchScope
                                ? (GlobalSearchScope)customScope
                                : GlobalSearchScope.projectScope(project);
    List<String> words = StringUtil.getWordsIn(findModel.getStringToFind());
    // if no words specified in search box, fallback to brute force search
    if (words.isEmpty()) return null;
    // hope long words are rare
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });
    Set<PsiFile> resultFiles = new THashSet<PsiFile>();
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      PsiFile[] files = cacheManager.getFilesWithWord(word, UsageSearchContext.ANY, scope, findModel.isCaseSensitive());
      if (files.length == 0) {
        resultFiles.clear();
        break;
      }

      final List<PsiFile> psiFiles = Arrays.asList(files);
      if (i == 0) {
        resultFiles.addAll(psiFiles);
      }
      else {
        resultFiles.retainAll(psiFiles);
      }
      filterMaskedFiles(resultFiles, fileMaskRegExp);
      if (resultFiles.isEmpty()) break;
    }

    // in case our word splitting is incorrect
    PsiFile[] allWordsFiles = cacheManager.getFilesWithWord(findModel.getStringToFind(), UsageSearchContext.ANY, scope, findModel.isCaseSensitive());
    resultFiles.addAll(Arrays.asList(allWordsFiles));
    filterMaskedFiles(resultFiles, fileMaskRegExp);

    return resultFiles;
  }

  private static GlobalSearchScope moduleContentScope(final Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    GlobalSearchScope result = null;
    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    for (VirtualFile root : contentRoots) {
      PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        GlobalSearchScope moduleContent = GlobalSearchScope.directoryScope(directory, true);
        if (result == null) {
          result = moduleContent;
        }
        else {
          result = result.uniteWith(moduleContent);
        }
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
    // $ is used to separate words when indexing plain-text files but not when indexing
    // Java identifiers, so we can't consistently break a string containing $ characters
    // into words
    return findModel.isWholeWordsOnly()
           && !findModel.isRegularExpressions()
           && findModel.getStringToFind().indexOf('$') < 0
           && (findModel.getCustomScope() == null || findModel.getCustomScope() instanceof GlobalSearchScope)
      ;
  }

  private static void addToUsages(@NotNull Project project,
                                  @NotNull Document document,
                                  @NotNull AsyncFindUsagesProcessListener consumer,
                                  @NotNull FindModel findModel,
                                  @NotNull final PsiFile psiFile,
                                  @NotNull final int[] count) {
    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    if (text != null) {
      int offset = 0;
      FindManager findManager = FindManager.getInstance(project);
      while (offset < textLength) {
        FindResult result = findManager.findString(text, offset, findModel);
        if (!result.isStringFound()) break;

        UsageInfo info = new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset());
        consumer.foundUsage(info);
        count[0]++;

        final int prevOffset = offset;
        offset = result.getEndOffset();

        if (prevOffset == offset) {
          // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
          ++offset;
        }
      }
    }
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

  public static class AsyncFindUsagesProcessListener2ProcessorAdapter implements AsyncFindUsagesProcessListener {
    private final Processor<Usage> processor;
    private int count;

    public AsyncFindUsagesProcessListener2ProcessorAdapter(Processor<Usage> _processor) {
      processor = _processor;
    }

    public void foundUsage(UsageInfo info) {
      ++count;
      processor.process(new UsageInfo2UsageAdapter(info));
    }

    public void findUsagesCompleted() {
    }

    public int getCount() {
      return count;
    }
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.*;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.findInProject.FindInProjectScopeService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public final class FindInProjectUtil {
  private static final int USAGES_PER_READ_ACTION = 100;

  @ApiStatus.Experimental
  public static final Key<Boolean> FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE = Key.create("find.in.files.non.indexable");

  private FindInProjectUtil() {}

  public static void setScope(@NotNull Project project, @NotNull FindModel model, @NotNull DataContext dataContext) {
    if (AdvancedSettings.getBoolean("ide.remember.last.search.scope")) {
      FindInProjectScopeService.getInstance(project).load(model);
    }
    else {
      setDirectoryName(model, dataContext);
    }
  }

  public static void setDirectoryName(@NotNull FindModel model, @NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      EditorSearchSession session = EditorSearchSession.SESSION_KEY.getData(dataContext);
      if (session != null) editor = session.getEditor();
    }
    PsiElement psiElement = null;
    if (project != null && editor == null && !DumbService.getInstance(project).isDumb()) {
      try {
        psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      }
      catch (IndexNotReadyException ignore) {}
    }

    String directoryName = null;

    if (psiElement instanceof PsiDirectory) {
      directoryName = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
      if (directoryName.isEmpty()) directoryName = null;
    }

    if (directoryName == null && psiElement instanceof PsiDirectoryContainer) {
      PsiDirectory[] directories = ((PsiDirectoryContainer)psiElement).getDirectories();
      directoryName = directories.length == 1 ? directories[0].getVirtualFile().getPresentableUrl():null;
    }

    if (directoryName == null) {
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (virtualFile != null) {
        if (virtualFile.isDirectory()) {
          directoryName = virtualFile.getPresentableUrl();
        }
        else {
          VirtualFile parent = virtualFile.getParent();
          if (parent != null && parent.isDirectory()) {
            if (editor == null) {
              directoryName = parent.getPresentableUrl();
            }
            else if (project != null) {
              FindInProjectSettings.getInstance(project).addDirectory(parent.getPresentableUrl());
            }
          }
        }
      }
    }

    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null) {
      model.setModuleName(module.getName());
      model.setDirectoryName(null);
      model.setCustomScope(false);
    }

    if (model.getModuleName() == null || editor == null) {
      if (directoryName != null) {
        model.setDirectoryName(directoryName);
        model.setCustomScope(false); // to select "Directory: " radio button
      }
    }

    for (FindInProjectExtension extension : FindInProjectExtension.EP_NAME.getExtensionList()) {
      boolean success = extension.initModelFromContext(model, dataContext);
      if (success) break;
    }

    // set project scope if we have no other settings
    model.setProjectScope(model.getDirectoryName() == null && model.getModuleName() == null && !model.isCustomScope());
  }

  /**
   * @return a VirtualFile representing directory to look in, if findModel defines it, or null otherwise.
   * Beware: returned directory may be {@link com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile} wrapper, so if
   * you want to compare it against regular {@link VirtualFile} you should {@link CacheAvoidingVirtualFile#asCacheable() unwrap}
   * it first.
   */
  public static @Nullable VirtualFile getDirectory(@NotNull FindModel findModel) {
    //TODO RC: maybe add a flag avoidCaching to the method call, and use .findFileByPath()/.findFileByPathWithoutCaching()
    //         accordingly?
    String directoryPath = findModel.getDirectoryName();
    if (findModel.isProjectScope() || StringUtil.isEmptyOrSpaces(directoryPath)) {
      return null;
    }

    String osIndependentDirectoryPath = FileUtilRt.toSystemIndependentName(directoryPath);
    VirtualFile directory = LocalFileSystem.getInstance().findFileByPathWithoutCaching(osIndependentDirectoryPath);
    if (directory == null || !directory.isDirectory()) {

      directory = null;

      // path doesn't contain file system prefix so try to find it inside archives (IDEA-216479)
      List<VirtualFileSystem> registeredPhysicalFileSystems = ((VirtualFileManagerImpl)VirtualFileManager.getInstance()).getPhysicalFileSystems();
      for (VirtualFileSystem fs : registeredPhysicalFileSystems) {
        if (!(fs instanceof ArchiveFileSystem afs)) continue;

        VirtualFile file = afs.findFileByPathWithoutCaching(osIndependentDirectoryPath);
        if (file != null && file.isDirectory()) {
          if (file.getChildren().length > 0) {
            directory = file;
            break;
          }
          if (directory == null) {
            directory = file;
          }
        }
      }

      if (directory == null && !osIndependentDirectoryPath.contains(JarFileSystem.JAR_SEPARATOR)) {
        //RC: shouldn't we iterate all archive FSes, as above -- not just JarFileSystem?
        directory = JarFileSystem.getInstance().findFileByPathWithoutCaching(osIndependentDirectoryPath + JarFileSystem.JAR_SEPARATOR);
      }
    }
    return directory;
  }

  /* filter can have form "*.js, !*_min.js", latter means except matched by *_min.js */
  public static @NotNull Condition<CharSequence> createFileMaskCondition(@Nullable String filter) throws PatternSyntaxException {
    if (StringUtil.isEmpty(filter)) {
      return Conditions.alwaysTrue();
    }

    StringBuilder pattern = new StringBuilder();
    StringBuilder negativePattern = new StringBuilder();
    List<String> masks = StringUtil.split(filter, ",");

    for (String mask : masks) {
      mask = mask.trim();
      if (mask.startsWith("!")) {
        negativePattern.append((negativePattern.isEmpty()) ? "" : "|").append("(").append(PatternUtil.convertToRegex(mask.substring(1))).append(")");
      }
      else {
        pattern.append((pattern.isEmpty()) ? "" : "|").append("(").append(PatternUtil.convertToRegex(mask)).append(")");
      }
    }

    if (pattern.isEmpty()) pattern.append(PatternUtil.convertToRegex("*"));

    return new Condition<>() {
      final Pattern regExp = Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
      final Pattern negativeRegExp = negativePattern.isEmpty() ? null : Pattern.compile(negativePattern.toString(), Pattern.CASE_INSENSITIVE);

      @Override
      public boolean value(CharSequence input) {
        return regExp.matcher(input).matches() && (negativeRegExp == null || !negativeRegExp.matcher(input).matches());
      }
    };
  }

  public static void findUsages(@NotNull FindModel findModel,
                                @NotNull Project project,
                                @NotNull Processor<? super UsageInfo> consumer,
                                @NotNull FindUsagesProcessPresentation processPresentation) {
    findUsages(findModel, project, processPresentation, Collections.emptySet(), consumer);
  }

  public static void findUsages(@NotNull FindModel findModel,
                                @NotNull Project project,
                                @NotNull FindUsagesProcessPresentation processPresentation,
                                @NotNull Set<? extends @NotNull VirtualFile> filesToStart,
                                @NotNull Processor<? super UsageInfo> consumer) {
    Runnable runnable = () -> new FindInProjectTask(findModel, project, filesToStart, true).findUsages(processPresentation, consumer);
    if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
      ProgressManager.getInstance().runProcess(runnable, new EmptyProgressIndicator());
    }
    else {
      runnable.run();
    }
  }

  public static void findUsages(@NotNull FindModel findModel,
                                @NotNull Project project,
                                @NotNull ProgressIndicator progressIndicator,
                                @NotNull FindUsagesProcessPresentation processPresentation,
                                @NotNull Set<? extends @NotNull VirtualFile> filesToStart,
                                @NotNull Processor<? super UsageInfo> consumer) {
    Runnable runnable = () -> new FindInProjectTask(findModel, project, filesToStart, false).findUsages(processPresentation, consumer);
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, progressIndicator);
  }

  static boolean processUsagesInFile(@NotNull PsiFile psiFile,
                                     @NotNull VirtualFile virtualFile,
                                     @NotNull FindModel findModel,
                                     @NotNull Processor<? super UsageInfo> consumer) {
    if (findModel.getStringToFind().isEmpty()) {
      return ReadAction.compute(() -> consumer.process(new UsageInfo(psiFile)));
    }
    if (virtualFile.getFileType().isBinary()) return true; // do not decompile .class files
    Document document = ReadAction.compute(() -> virtualFile.isValid() ? FileDocumentManager.getInstance().getDocument(virtualFile) : null);
    if (document == null) return true;
    ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
    if (current == null) throw new IllegalStateException("must find usages under progress");
    ProgressIndicator indicator = ProgressWrapper.unwrapAll(current);
    TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.getFrom(indicator);
    int before;
    int[] offsetRef = {0};
    do {
      tooManyUsagesStatus.pauseProcessingIfTooManyUsages(); // wait for user out of read action
      before = offsetRef[0];
      boolean success = ReadAction.compute(() -> !psiFile.isValid() ||
                                             processSomeOccurrencesInFile(document, findModel, psiFile, offsetRef, consumer));
      if (!success) {
        return false;
      }
    }
    while (offsetRef[0] != before);
    return true;
  }

  private static boolean processSomeOccurrencesInFile(@NotNull Document document,
                                                      @NotNull FindModel findModel,
                                                      @NotNull PsiFile psiFile,
                                                      int @NotNull [] offsetRef,
                                                      @NotNull Processor<? super UsageInfo> consumer) {
    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    int offset = offsetRef[0];

    Project project = psiFile.getProject();

    FindManager findManager = FindManager.getInstance(project);
    int count = 0;
    while (offset < textLength) {
      FindResult result = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!result.isStringFound()) break;

      int prevOffset = offset;
      offset = result.getEndOffset();
      if (prevOffset == offset || offset == result.getStartOffset()) {
        // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
        ++offset;
      }

      SearchScope customScope = findModel.getCustomScope();
      if (customScope instanceof LocalSearchScope) {
        if (!((LocalSearchScope)customScope).containsRange(psiFile, result)) continue;
      }
      UsageInfo info = new FindResultUsageInfo(findManager, psiFile, prevOffset, findModel, result);
      if (!consumer.process(info)) {
        return false;
      }
      count++;

      if (count >= USAGES_PER_READ_ACTION) {
        break;
      }
    }
    offsetRef[0] = offset;
    return true;
  }

  public static @NotNull @Nls String getTitleForScope(@NotNull FindModel findModel) {
    String scopeName;
    if (findModel.isProjectScope()) {
      scopeName = FindBundle.message("find.scope.project.title");
    }
    else if (findModel.getModuleName() != null) {
      scopeName = FindBundle.message("find.scope.module.title", findModel.getModuleName());
    }
    else if(findModel.getCustomScopeName() != null) {
      scopeName = findModel.getCustomScopeName();
    }
    else {
      scopeName = FindBundle.message("find.scope.directory.title", findModel.getDirectoryName());
    }

    String result = scopeName;
    if (findModel.getFileFilter() != null) {
      result += " "+FindBundle.message("find.scope.files.with.mask", findModel.getFileFilter());
    }

    return result;
  }

  public static @NotNull UsageViewPresentation setupViewPresentation(@NotNull FindModel findModel) {
    return setupViewPresentation(FindSettings.getInstance().isShowResultsInSeparateView(), findModel);
  }

  public static @NotNull UsageViewPresentation setupViewPresentation(boolean toOpenInNewTab, @NotNull FindModel findModel) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    setupViewPresentation(presentation, toOpenInNewTab, findModel);
    return presentation;
  }

  public static void setupViewPresentation(@NotNull UsageViewPresentation presentation, @NotNull FindModel findModel) {
    setupViewPresentation(presentation, FindSettings.getInstance().isShowResultsInSeparateView(), findModel);
  }

  public static void setupViewPresentation(@NotNull UsageViewPresentation presentation, boolean toOpenInNewTab, @NotNull FindModel findModel) {
    String scope = getTitleForScope(findModel);
    String stringToFind = findModel.getStringToFind();
    String stringToReplace = findModel.getStringToReplace();
    presentation.setScopeText(scope);
    if (stringToFind.isEmpty()) {
      if (!scope.isEmpty()) {
        scope = Character.toLowerCase(scope.charAt(0)) + scope.substring(1);
      }
      presentation.setTabText(FindBundle.message("tab.title.files"));
      presentation.setToolwindowTitle(FindBundle.message("tab.title.files.in.scope", scope));
      presentation.setUsagesString(LangBundle.message("files"));
    }
    else {
      FindModel.SearchContext searchContext = findModel.getSearchContext();
      String contextText = "";
      if (searchContext != FindModel.SearchContext.ANY) {
        contextText = FindBundle.message("find.context.presentation.scope.label", getPresentableName(searchContext));
      }
      if (!findModel.isReplaceState()) {
        presentation.setTabText(FindBundle.message("find.usage.view.tab.text", stringToFind, contextText));
        presentation.setToolwindowTitle(FindBundle.message("find.usage.view.toolwindow.title", stringToFind, scope, contextText));
      }
      else {
        presentation.setTabText(FindBundle.message("replace.usage.view.tab.text", stringToFind, stringToReplace, contextText));
        presentation.setToolwindowTitle(FindBundle.message("replace.usage.view.toolwindow.title", stringToFind, stringToReplace, scope, contextText));
      }
      presentation.setSearchString(FindBundle.message("find.occurrences.search.string", stringToFind, searchContext.ordinal()));
      presentation.setCodeUsagesString(FindBundle.message("found.occurrences"));
    }
    presentation.setOpenInNewTab(toOpenInNewTab);
    presentation.setCodeUsages(false);
    presentation.setUsageTypeFilteringAvailable(true);
    if (findModel.isReplaceState() && findModel.isRegularExpressions()) {
      presentation.setSearchPattern(findModel.compileRegExp());
      presentation.setReplaceString(findModel.getStringToReplace());
    }
    else {
      presentation.setSearchPattern(null);
      presentation.setReplaceString(null);
    }
    presentation.setCaseSensitive(findModel.isCaseSensitive());
    presentation.setPreserveCase(findModel.isPreserveCase());
    presentation.setReplaceMode(findModel.isReplaceState());
  }

  /** @deprecated please use {@link #setupProcessPresentation(UsageViewPresentation)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public static @NotNull FindUsagesProcessPresentation setupProcessPresentation(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    return setupProcessPresentation(presentation);
  }

  /** @deprecated please use {@link #setupProcessPresentation(boolean, UsageViewPresentation)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public static @NotNull FindUsagesProcessPresentation setupProcessPresentation(@NotNull Project project,
                                                                                boolean showPanelIfOnlyOneUsage,
                                                                                @NotNull UsageViewPresentation presentation) {
    return setupProcessPresentation(showPanelIfOnlyOneUsage, presentation);
  }

  public static @NotNull FindUsagesProcessPresentation setupProcessPresentation(@NotNull UsageViewPresentation presentation) {
    return setupProcessPresentation(!FindSettings.getInstance().isSkipResultsWithOneUsage(), presentation);
  }

  public static @NotNull FindUsagesProcessPresentation setupProcessPresentation(boolean showPanelIfOnlyOneUsage, @NotNull UsageViewPresentation presentation) {
    var processPresentation = new FindUsagesProcessPresentation(presentation);
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);
    return processPresentation;
  }

  private static List<PsiElement> getTopLevelRegExpChars(String regExpText, Project project) {
    String regexFileName = "A.regexp";
    FileType regexFileType = FileTypeRegistry.getInstance().getFileTypeByFileName(regexFileName);
    if (regexFileType == UnknownFileType.INSTANCE) return Collections.emptyList();

    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(regexFileName, regexFileType, regExpText);
    List<PsiElement> result = null;
    PsiElement[] children = file.getChildren();

    for (PsiElement child:children) {
      PsiElement[] grandChildren = child.getChildren();
      if (grandChildren.length != 1) return Collections.emptyList(); // a | b, more than one branch, can not predict in current way

      for(PsiElement grandGrandChild:grandChildren[0].getChildren()) {
        if (result == null) result = new ArrayList<>();
        result.add(grandGrandChild);
      }
    }
    return result != null ? result : Collections.emptyList();
  }

  public static @NotNull String extractStringToFind(@NotNull String regexp, @NotNull Project project) {
    return ReadAction.compute(() -> {
      List<PsiElement> topLevelRegExpChars = getTopLevelRegExpChars("a", project);
      if (topLevelRegExpChars.size() != 1) return " ";

      // leave only top level regExpChars

      Class<?> regExpCharPsiClass = topLevelRegExpChars.get(0).getClass();
      return getTopLevelRegExpChars(regexp, project)
        .stream()
        .map(psi -> {
          if (regExpCharPsiClass.isInstance(psi)) {
            String text = psi.getText();
            if (!text.startsWith("\\")) return text;
          }
          return " ";
        })
        .collect(Collectors.joining());
    });
  }

  public static @NotNull String buildStringToFindForIndicesFromRegExp(@NotNull String stringToFind, @NotNull Project project) {
    return StringUtil.trim(StringUtil.join(extractStringToFind(stringToFind, project), " "));
  }

  public static void initStringToFindFromDataContext(FindModel findModel, @NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    FindUtil.initStringToFindWithSelection(findModel, editor);
    if (editor == null || !editor.getSelectionModel().hasSelection()) {
      FindUtil.useFindStringFromFindInFileModel(findModel, CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext));
    }
  }

  public static class StringUsageTarget implements ConfigurableUsageTarget, ItemPresentation, UiDataProvider {
    protected final @NotNull Project myProject;
    protected final @NotNull FindModel myFindModel;

    public StringUsageTarget(@NotNull Project project, @NotNull FindModel findModel) {
      myProject = project;
      myFindModel = findModel.clone();
    }

    @Override
    public @NotNull String getPresentableText() {
      UsageViewPresentation presentation = setupViewPresentation(false, myFindModel);
      return presentation.getToolwindowTitle();
    }

    @Override
    public @Nls @NotNull String getLongDescriptiveName() {
      return getPresentableText();
    }

    @Override
    public Icon getIcon(boolean open) {
      return AllIcons.Actions.Find;
    }

    @Override
    public void findUsages() {
      FindInProjectManager.getInstance(myProject).startFindInProject(myFindModel);
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public String getName() {
      return myFindModel.getStringToFind().isEmpty() ? myFindModel.getFileFilter() : myFindModel.getStringToFind();
    }

    @Override
    public ItemPresentation getPresentation() {
      return this;
    }

    @Override
    public void showSettings() {
      Content selectedContent = UsageViewContentManager.getInstance(myProject).getSelectedContent(true);
      JComponent component = selectedContent == null ? null : selectedContent.getComponent();
      FindInProjectManager findInProjectManager = FindInProjectManager.getInstance(myProject);
      findInProjectManager.findInProject(DataManager.getInstance().getDataContext(component), myFindModel);
    }

    @Override
    public KeyboardShortcut getShortcut() {
      return ActionManager.getInstance().getKeyboardShortcut("FindInPath");
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.lazy(UsageView.USAGE_SCOPE, () -> {
        return getScopeFromModel(myProject, myFindModel);
      });
    }
  }

  private static void addSourceDirectoriesFromLibraries(@NotNull Project project,
                                                        @NotNull VirtualFile directory,
                                                        @NotNull Collection<? super VirtualFile> outSourceRoots) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    // if we already are in the sources, search just in this directory only
    if (!index.isInLibraryClasses(directory)) return;
    VirtualFile classRoot = index.getClassRootForFile(directory);
    if (classRoot == null) return;
    String relativePath = VfsUtilCore.getRelativePath(directory, classRoot);
    if (relativePath == null) return;

    Collection<VirtualFile> otherSourceRoots = new HashSet<>();

    // if we are in the library sources, return (to search in this directory only)
    // otherwise, if we outside sources or in a jar directory, add directories from other source roots
    searchForOtherSourceDirs:
    for (OrderEntry entry : index.getOrderEntriesForFile(directory)) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library == null) continue;
        // note: getUrls() returns jar directories too
        String[] sourceUrls = library.getUrls(OrderRootType.SOURCES);
        for (String sourceUrl : sourceUrls) {
          if (VfsUtilCore.isEqualOrAncestor(sourceUrl, directory.getUrl())) {
            // already in this library sources, no need to look for another source root
            otherSourceRoots.clear();
            break searchForOtherSourceDirs;
          }
          // otherwise we may be inside the jar file in a library which is configured as a jar directory
          // in which case we have no way to know whether this is a source jar or classes jar - so try to locate the source jar
        }
      }
      for (VirtualFile sourceRoot : entry.getFiles(OrderRootType.SOURCES)) {
        VirtualFile sourceFile = sourceRoot.findFileByRelativePath(relativePath);
        if (sourceFile != null) {
          otherSourceRoots.add(sourceFile);
        }
      }
    }
    outSourceRoots.addAll(otherSourceRoots);
  }

  public static @NotNull GlobalSearchScope getGlobalSearchScope(@NotNull Project project, @NotNull FindModel findModel) {
    return GlobalSearchScopeUtil.toGlobalSearchScope(getScopeFromModel(project, findModel), project);
  }

  static @NotNull SearchScope getScopeFromModel(@NotNull Project project, @NotNull FindModel findModel) {
    SearchScope customScope = findModel.isCustomScope() ? findModel.getCustomScope() : null;
    VirtualFile directory = getDirectory(findModel);
    Module module = findModel.getModuleName() == null ? null : ModuleManager.getInstance(project).findModuleByName(findModel.getModuleName());
    // do not alter custom scope in any way, learn from history
    return customScope != null ? customScope :
           // we don't have to check for myProjectFileIndex.isExcluded(file) here like FindInProjectTask.collectFilesInScope() does
           // because all found usages are guaranteed to be not in excluded dir
           directory != null ? forDirectory(project, findModel.isWithSubdirectories(), directory) :
           module != null ? module.getModuleContentScope() :
           findModel.isProjectScope() ? ProjectScope.getContentScope(project) :
           GlobalSearchScope.allScope(project);
  }

  private static @NotNull GlobalSearchScope forDirectory(@NotNull Project project,
                                                         boolean withSubdirectories,
                                                         @NotNull VirtualFile directory) {
    if (directory instanceof CacheAvoidingVirtualFile cacheAvoidingDir) {
      directory = cacheAvoidingDir.asCacheable();
      if(directory == null) {
        return GlobalSearchScope.EMPTY_SCOPE;
      }
    }
    Set<VirtualFile> result = new LinkedHashSet<>();
    result.add(directory);
    addSourceDirectoriesFromLibraries(project, directory, result);
    VirtualFile[] directories = result.toArray(VirtualFile.EMPTY_ARRAY);
    GlobalSearchScope scope = GlobalSearchScopesCore.directoriesScope(project, withSubdirectories, directories);
    for (FindInDirectoryScopeProvider provider : FindInDirectoryScopeProvider.EP_NAME.getExtensionList()) {
      scope = provider.alterDirectorySearchScope(project, directory, withSubdirectories, scope);
    }
    return scope;
  }

  public static void initFileFilter(@NotNull JComboBox<? super String> fileFilter, @NotNull JCheckBox useFileFilter) {
    fileFilter.setEditable(true);
    String[] fileMasks = FindSettings.getInstance().getRecentFileMasks();
    for (int i = fileMasks.length - 1; i >= 0; i--) {
      fileFilter.addItem(fileMasks[i]);
    }
    fileFilter.setEnabled(false);

    useFileFilter.addActionListener(
      __ -> {
        if (useFileFilter.isSelected()) {
          fileFilter.setEnabled(true);
          fileFilter.getEditor().selectAll();
          fileFilter.getEditor().getEditorComponent().requestFocusInWindow();
        }
        else {
          fileFilter.setEnabled(false);
        }
      }
    );
  }

  public static @Nls(capitalization = Title) @NotNull String getPresentableName(@NotNull FindModel.SearchContext searchContext) {
    @PropertyKey(resourceBundle = "messages.FindBundle") String messageKey = switch (searchContext) {
      case ANY -> "find.context.anywhere.scope.label";
      case EXCEPT_COMMENTS -> "find.context.except.comments.scope.label";
      case EXCEPT_STRING_LITERALS -> "find.context.except.literals.scope.label";
      case EXCEPT_COMMENTS_AND_STRING_LITERALS -> "find.context.except.comments.and.literals.scope.label";
      case IN_COMMENTS -> "find.context.in.comments.scope.label";
      case IN_STRING_LITERALS -> "find.context.in.literals.scope.label";
    };
    return FindBundle.message(messageKey);
  }
}

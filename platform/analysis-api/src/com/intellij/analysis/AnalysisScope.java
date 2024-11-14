// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class AnalysisScope {
  private static final Logger LOG = Logger.getInstance(AnalysisScope.class);

  public static final int PROJECT = 1;
  public static final int DIRECTORY = 2;
  public static final int FILE = 3;
  public static final int MODULE = 4;
  protected static final int PACKAGE = 5;
  public static final int INVALID = 6;
  public static final int MODULES = 7;
  public static final int CUSTOM = 8;
  public static final int VIRTUAL_FILES = 9;
  public static final int UNCOMMITTED_FILES = 10;
  @MagicConstant(intValues = {PROJECT, DIRECTORY, FILE, MODULE, PACKAGE, INVALID, MODULES, CUSTOM, VIRTUAL_FILES, UNCOMMITTED_FILES})
  public @interface Type { }

  private final @NotNull Project myProject;
  protected List<Module> myModules;
  protected Module myModule;
  protected PsiElement myElement;
  protected final SearchScope myScope;
  private boolean mySearchInLibraries;
  private GlobalSearchScope myFilter;
  @Type protected int myType;

  private Set<? extends VirtualFile> myVFiles;  // initial files and directories the scope is configured on
  private VirtualFileSet myFilesSet; // set of files (not directories) this scope consists of. calculated in getFilesSet()

  private boolean myIncludeTestSource = true;
  private boolean myAnalyzeInjectedCode = true;

  public AnalysisScope(@NotNull Project project) {
    myProject = project;
    myElement = null;
    myModules = null;
    myModule = null;
    myScope = null;
    myType = PROJECT;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull Module module) {
    myProject = module.getProject();
    myElement = null;
    myModules = null;
    myScope = null;
    myModule = module;
    myType = MODULE;
    myVFiles = null;
  }

  public AnalysisScope(Module @NotNull [] modules) {
    myModules = Arrays.asList(modules);
    myModule = null;
    myProject = modules[0].getProject();
    myElement = null;
    myScope = null;
    myType = MODULES;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull PsiDirectory psiDirectory) {
    this(psiDirectory, null);
  }

  public AnalysisScope(@NotNull PsiDirectory psiDirectory, @Nullable Module module) {
    myProject = psiDirectory.getProject();
    myModules = null;
    myModule = module;
    myScope = null;
    myElement = psiDirectory;
    myType = DIRECTORY;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull PsiFile psiFile) {
    this(psiFile, null);
  }

  public AnalysisScope(@NotNull PsiFile psiFile, @Nullable Module module) {
    myProject = psiFile.getProject();
    myElement = psiFile;
    myModule = module;
    myModules = null;
    myScope = null;
    myType = FILE;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull SearchScope scope, @NotNull Project project) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = scope;
    myType = CUSTOM;
    mySearchInLibraries = scope instanceof GlobalSearchScope && ((GlobalSearchScope)scope).isSearchInLibraries();
    myVFiles = null;
  }

  public AnalysisScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> virtualFiles) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = null;
    VirtualFileSet files = VfsUtilCore.createCompactVirtualFileSet(virtualFiles);
    files.freeze();
    myVFiles = files;
    myType = VIRTUAL_FILES;
  }

  public void setSearchInLibraries(boolean searchInLibraries) {
    LOG.assertTrue(myFilesSet == null, "don't modify AnalysisScope after it has been used");
    mySearchInLibraries = searchInLibraries;
  }

  public void setIncludeTestSource(boolean includeTestSource) {
    LOG.assertTrue(myFilesSet == null, "don't modify AnalysisScope after it has been used");
    myIncludeTestSource = includeTestSource;
  }

  public void setAnalyzeInjectedCode(boolean analyzeInjectedCode) {
    LOG.assertTrue(myFilesSet == null, "don't modify AnalysisScope after it has been used");
    myAnalyzeInjectedCode = analyzeInjectedCode;
  }

  protected @NotNull Processor<? super VirtualFile> createFileSearcher(@NotNull Collection<? super VirtualFile> addTo) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(AnalysisBundle.message("scanning.scope.progress.title"));
    }
    return virtualFile -> {
      addTo.add(virtualFile);
      return true;
    };
  }

  private boolean isFilteredOut(@NotNull VirtualFile virtualFile) {
    GlobalSearchScope filter = myFilter;
    if (filter != null && !filter.contains(virtualFile)) {
      return true;
    }
    return !myIncludeTestSource && ReadAction.compute(() -> TestSourcesFilter.isTestSources(virtualFile, myProject));
  }

  private @NotNull FileIndex getFileIndex() {
    return myModule == null ?
           ProjectRootManager.getInstance(myProject).getFileIndex() :
           ModuleRootManager.getInstance(myModule).getFileIndex();
  }

  private static @NotNull String displayProjectRelativePath(@NotNull PsiFileSystemItem item, @Nullable Module module) {
    VirtualFile virtualFile = item.getVirtualFile();
    LOG.assertTrue(virtualFile != null, item);
    String filePath = ProjectUtilCore.displayFilePath(item.getProject(), virtualFile);
    return module == null ? filePath : ProjectUtilCore.appendModuleName(module, filePath, false);
  }

  public boolean contains(@NotNull PsiElement psiElement) {
    VirtualFile file = psiElement.getContainingFile().getVirtualFile();
    return file != null && contains(file);
  }

  public boolean contains(@NotNull VirtualFile file) {
    if (myFilesSet == null) {
      if (myType == CUSTOM) {
        // optimization
        if (myScope != null) return myScope.contains(file);
      }
      if (myType == PROJECT) {  //optimization
        ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        return index.isInContent(file) && !isFilteredOut(file);
      }
    }

    return getFileSet().contains(file);
  }

  protected @NotNull VirtualFileSet createFilesSet() {
    VirtualFileSet fileSet = VfsUtilCore.createCompactVirtualFileSet();
    switch (myType) {
      case FILE -> {
        fileSet.add(((PsiFileSystemItem)myElement).getVirtualFile());
        fileSet.freeze();
      }
      case DIRECTORY, PROJECT, MODULES, MODULE, CUSTOM -> {
        long timeStamp = System.currentTimeMillis();
        accept(createFileSearcher(fileSet));
        fileSet.freeze();
        LOG.info("Scanning scope took " + (System.currentTimeMillis() - timeStamp) + " ms");
      }
      case VIRTUAL_FILES -> {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        for (VirtualFile vFile : myVFiles) {
          VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor<Void>() {
            @Override
            public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
              boolean ignored = ReadAction.compute(() -> fileIndex.isExcluded(file));
              if (!ignored && !file.isDirectory()) {
                fileSet.add(file);
              }
              return ignored ? SKIP_CHILDREN : CONTINUE;
            }
          });
        }
        fileSet.freeze();
      }
      default -> throw new IllegalStateException("Invalid type: " + myType + "; can't create file set off it");
    }
    return fileSet;
  }


  public void accept(@NotNull PsiElementVisitor visitor) {
    acceptImpl(visitor, false);
  }

  /**
   * A drop-in replacement for {@link #accept(PsiElementVisitor)} that invokes the visitor in a non-blocking cancellable read action,
   * so that the visitor can be interrupted and restarted several times on the same file.
   * The visitor must support this workflow, i.e. be idempotent.
   */
  public void acceptIdempotentVisitor(@NotNull PsiElementVisitor visitor) {
    acceptImpl(visitor, true);
  }

  private void acceptImpl(@NotNull PsiElementVisitor visitor, boolean idempotent) {
    boolean needReadAction = !ApplicationManager.getApplication().isReadAccessAllowed();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    FileIndex fileIndex = getFileIndex();
    accept(file -> {
      if (file.isDirectory()) return true;
      if (ProjectCoreUtil.isProjectOrWorkspaceFile(file)) return true;
      boolean isInContent = ReadAction.compute(() -> fileIndex.isInContent(file));
      if (isInContent && !isFilteredOut(file)
          && !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
        return processFile(file, visitor, psiManager, needReadAction, idempotent);
      }
      return true;
    });
  }

  public boolean accept(@NotNull Processor<? super VirtualFile> processor) {
    if (myType == VIRTUAL_FILES) {
      return getFileSet().process(file -> isFilteredOut(file) || processor.process(file));
    }
    if (myFilesSet != null) {
      return myFilesSet.process(processor);
    }
    FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (myScope instanceof GlobalSearchScope) {
      ContentIterator contentIterator = createScopeIterator(processor, myScope);
      if (!projectFileIndex.iterateContent(contentIterator)) return false;
      if (mySearchInLibraries) {
        VirtualFile[] libraryRoots = LibraryUtil.getLibraryRoots(myProject, false, false);
        for (VirtualFile libraryRoot : libraryRoots) {
          if (!VfsUtilCore.iterateChildrenRecursively(libraryRoot, VirtualFileFilter.ALL, contentIterator)) return false;
        }
      }
      return true;
    }
    if (myScope instanceof LocalSearchScope) {
      PsiElement[] psiElements = ((LocalSearchScope)myScope).getScope();
      Set<VirtualFile> files = new HashSet<>();
      for (PsiElement element : psiElements) {
        VirtualFile file = ReadAction.compute(() -> PsiUtilCore.getVirtualFile(element));
        if (file != null && files.add(file)) {
          if (!processor.process(file)) return false;
        }
      }
      return true;
    }
    List<Module> modules = myModule != null ? Collections.singletonList(myModule) : myModules;
    if (modules != null) {
      for (Module module : modules) {
        FileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        if (!moduleFileIndex.iterateContent(createScopeIterator(processor, null))) {
          return false;
        }
      }
      return true;
    }

    if (myElement instanceof PsiDirectory) {
      return accept((PsiDirectory)myElement, processor);
    }
    if (myElement != null) {
      VirtualFile file = ReadAction.compute(() -> PsiUtilCore.getVirtualFile(myElement));
      return file == null || processor.process(file);
    }

    return projectFileIndex.iterateContent(createScopeIterator(processor, null));
  }

  private @NotNull VirtualFileSet getFileSet() {
    VirtualFileSet fileSet = myFilesSet;
    if (fileSet == null) {
      myFilesSet = fileSet = createFilesSet();
    }
    return fileSet;
  }

  private @NotNull ContentIterator createScopeIterator(@NotNull Processor<? super VirtualFile> processor, @Nullable SearchScope searchScope) {
    return fileOrDir -> {
      boolean isInScope = ReadAction.compute(() -> {
        if (searchScope != null && !searchScope.contains(fileOrDir)) return false;
        if (isFilteredOut(fileOrDir)) return false;
        return !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(fileOrDir, myProject);
      });
      return !isInScope || processor.process(fileOrDir);
    };
  }

  private static boolean processFile(@NotNull VirtualFile vFile,
                                     @NotNull PsiElementVisitor visitor,
                                     @NotNull PsiManager psiManager,
                                     boolean needReadAction,
                                     boolean idempotent) {
    if (needReadAction && !ApplicationManager.getApplication().isDispatchThread()) {
      Project project = psiManager.getProject();
      if (idempotent) {
        ReadAction
          .nonBlocking(() -> doProcessFile(visitor, psiManager, vFile))
          .withDocumentsCommitted(project)
          .inSmartMode(project)
          .executeSynchronously();
      }
      else {
        commitAndRunInSmartMode(() -> doProcessFile(visitor, psiManager, vFile), project);
      }
    }
    else {
      doProcessFile(visitor, psiManager, vFile);
    }
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return indicator == null || !indicator.isCanceled();
  }

  private static void commitAndRunInSmartMode(Runnable runnable, Project project) {
    while (true) {
      DumbService dumbService = DumbService.getInstance(project);
      dumbService.waitForSmartMode();
      boolean passed = PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
        if (dumbService.isDumb()) return false;
        runnable.run();
        return true;
      });
      if (passed) {
        break;
      }
    }
  }

  private static boolean shouldHighlightFile(@NotNull PsiFile file) {
    return ProblemHighlightFilter.shouldProcessFileInBatch(file);
  }

  public boolean containsModule(@NotNull Module module) {
    return switch (myType) {
      case PROJECT -> true;
      case MODULE -> myModule == module;
      case MODULES -> myModules.contains(module);
      case CUSTOM -> {
        if (module.isDisposed()) yield false;
        for (VirtualFile file : ModuleRootManager.getInstance(module).getSourceRoots()) {
          if (myScope.contains(file)) yield true;
        }
        yield false;
      }
      default -> false;
    };
  }

  private static void doProcessFile(@NotNull PsiElementVisitor visitor, @NotNull PsiManager psiManager, @NotNull VirtualFile vFile) {
    ProgressManager.checkCanceled();
    if (!vFile.isValid()) return;

    PsiFile psiFile = psiManager.findFile(vFile);
    if (psiFile == null || !shouldHighlightFile(psiFile)) return;
    psiFile.accept(visitor);
    InjectedLanguageManager.getInstance(psiManager.getProject()).dropFileCaches(psiFile);
  }

  protected boolean accept(@NotNull PsiDirectory dir, @NotNull Processor<? super VirtualFile> processor) {
    Project project = dir.getProject();
    //we should analyze generated source files only if the action is explicitly invoked for a directory located under generated roots
    boolean processGeneratedFiles = GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(dir.getVirtualFile(), project);
    return VfsUtilCore.iterateChildrenRecursively(dir.getVirtualFile(), VirtualFileFilter.ALL, fileOrDir -> {
      if (isFilteredOut(fileOrDir)) return true;
      if (!processGeneratedFiles && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(fileOrDir, project)) return true;
      return fileOrDir.isDirectory() || processor.process(fileOrDir);
    });
  }

  public boolean isValid() {
    if (myModules != null){
      for (Module module : myModules) {
        if (module.isDisposed()) return false;
      }
      return true;
    }
    if (myModule != null) return !myModule.isDisposed();
    if (myElement != null) {
      return myElement.isValid();
    }
    return myType == VIRTUAL_FILES || myType == CUSTOM || myType == PROJECT;
  }

  @Type
  public int getScopeType() {
    return myType;
  }

  public @NotNull @Nls String getDisplayName() {
    return switch (myType) {
      case CUSTOM -> myScope.getDisplayName();
      case MODULE -> AnalysisBundle.message("scope.option.module", pathToName(myModule.getModuleFilePath()));
      case MODULES -> {
        String modules = StringUtil.join(myModules, module -> pathToName(module.getModuleFilePath()), ", ");
        yield AnalysisBundle.message("scope.module.list", modules, myModules.size());
      }
      case PROJECT -> AnalysisBundle.message("scope.project", myProject.getName());
      case FILE -> AnalysisBundle.message("scope.file", displayProjectRelativePath((PsiFileSystemItem)myElement, myModule));
      case DIRECTORY -> AnalysisBundle.message("scope.directory", displayProjectRelativePath((PsiFileSystemItem)myElement, myModule));
      case VIRTUAL_FILES -> AnalysisBundle.message("scope.virtual.files");
      default -> "";
    };
  }

  public @NotNull @Nls String getShortenName(){
    return switch (myType) {
      case CUSTOM -> myScope.getDisplayName();
      case MODULE -> AnalysisBundle.message("scope.option.module", myModule.getName());
      case MODULES -> {
        String modules = StringUtil.join(myModules, Module::getName, ", ");
        yield AnalysisBundle.message("scope.module.list", modules, myModules.size());
      }
      case PROJECT -> AnalysisBundle.message("scope.project", myProject.getName());
      case FILE -> {
        String relativePath = getRelativePath();
        yield AnalysisBundle.message("scope.file", relativePath);
      }
      case DIRECTORY -> {
        String relativeDirPath = getRelativePath();
        yield AnalysisBundle.message("scope.directory", relativeDirPath);
      }
      case VIRTUAL_FILES -> AnalysisBundle.message("scope.selected.files");
      default -> "";
    };
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @Nullable Module getModule() {
    return myModule;
  }

  public @NotNull List<Module> getModules() {
    return myModules == null ? Collections.emptyList() : Collections.unmodifiableList(myModules);
  }

  public @Nullable PsiElement getElement() {
    return myElement;
  }

  public @NotNull Set<VirtualFile> getFiles() {
    //noinspection unchecked
    return myVFiles == null ? Collections.emptySet() : (Set<VirtualFile>)myVFiles;
  }

  private @NotNull String getRelativePath() {
    String relativePath = displayProjectRelativePath((PsiFileSystemItem)myElement, myModule);
    if (relativePath.length() > 100) {
      return ((PsiFileSystemItem)myElement).getName();
    }
    return relativePath;
  }

  private static @NotNull String pathToName(@NotNull String path) {
    File file = new File(path);
    return FileUtilRt.getNameWithoutExtension(file.getName());
  }

  public int getFileCount() {
    return getFileSet().size();
  }

  public void invalidate() {
    if (myType == VIRTUAL_FILES) {
      List<? extends VirtualFile> valid = ContainerUtil.filter(myVFiles, virtualFile -> virtualFile != null && virtualFile.isValid());
      VirtualFileSet files = VfsUtilCore.createCompactVirtualFileSet(valid);
      files.freeze();
      myVFiles = files;
    }
    myFilesSet = null;
  }

  public boolean containsSources(boolean isTest) {
    if (myElement != null) {
      Project project = myElement.getProject();
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (myElement instanceof PsiDirectory) {
        VirtualFile directory = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (index.isInSourceContent(directory)) {
          return isTest == TestSourcesFilter.isTestSources(directory, myProject);
        }
      }
      else if (myElement instanceof PsiFile) {
        VirtualFile file = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (file != null) {
          return isTest == TestSourcesFilter.isTestSources(file, myProject);
        }
      }
    }
    return true;
  }

  public @NotNull AnalysisScope getNarrowedComplementaryScope(@NotNull Project defaultProject) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    HashSet<Module> modules = new HashSet<>();
    if (myType == FILE || myType == DIRECTORY) {
      VirtualFile vFile = ((PsiFileSystemItem)myElement).getVirtualFile();
      modules.addAll(getAllInterestingModules(fileIndex, vFile));
    }
    else if (myType == MODULE) {
      modules.add(myModule);
    }
    else if (myType == MODULES) {
      modules.addAll(myModules);
    }
    return collectScopes(defaultProject, modules);
  }

  protected static @NotNull AnalysisScope collectScopes(@NotNull Project defaultProject, @NotNull Set<? extends Module> modules) {
    if (modules.isEmpty()) {
      return new AnalysisScope(defaultProject);
    }
    Module[] allModules = ModuleManager.getInstance(defaultProject).getModules();
    Set<Module> modulesToAnalyze = new HashSet<>();
    for (Module module : modules) {
      modulesToAnalyze.addAll(getDirectBackwardDependencies(module, allModules));
      modulesToAnalyze.addAll(getExportBackwardDependencies(module, allModules));
      modulesToAnalyze.add(module);
    }
    return new AnalysisScope(modulesToAnalyze.toArray(Module.EMPTY_ARRAY));
  }

  private static @NotNull Set<Module> getExportBackwardDependencies(@NotNull Module fromModule, Module @NotNull [] allModules) {
    Set<Module> result = new HashSet<>();
    for (Module module : allModules) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof ModuleOrderEntry && ((ExportableOrderEntry)orderEntry).isExported() &&
            fromModule == ((ModuleOrderEntry)orderEntry).getModule()) {
          result.addAll(getDirectBackwardDependencies(module, allModules));
        }
      }
    }
    return result;
  }

  private static @NotNull Set<Module> getDirectBackwardDependencies(@NotNull Module module, Module @NotNull [] allModules) {
    Set<Module> result = new HashSet<>();
    for (Module dependency : allModules) {
      if (ArrayUtil.find(ModuleRootManager.getInstance(dependency).getDependencies(), module) > -1) {
        result.add(dependency);
      }
    }
    return result;
  }

  protected static @NotNull HashSet<Module> getAllInterestingModules(@NotNull ProjectFileIndex fileIndex, @NotNull VirtualFile vFile) {
    HashSet<Module> modules = new HashSet<>();
    if (fileIndex.isInLibrary(vFile)) {
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(vFile)) {
        modules.add(orderEntry.getOwnerModule());
      }
    }
    else {
      modules.add(fileIndex.getModuleForFile(vFile));
    }
    return modules;
  }

  public @NotNull SearchScope toSearchScope() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return switch (myType) {
      case CUSTOM -> myScope;
      case DIRECTORY -> GlobalSearchScopesCore.directoryScope((PsiDirectory)myElement, true);
      case FILE -> GlobalSearchScope.fileScope((PsiFile)myElement);
      case INVALID -> LocalSearchScope.EMPTY;
      case MODULE -> {
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(myModule);
        yield myIncludeTestSource
              ? moduleScope
              : GlobalSearchScope.notScope(GlobalSearchScopesCore.projectTestScope(myModule.getProject())).intersectWith(moduleScope);
      }
      case MODULES ->
        GlobalSearchScope.union(myModules.stream().map(m -> GlobalSearchScope.moduleScope(m)).toArray(GlobalSearchScope[]::new));
      case PROJECT ->
        myIncludeTestSource ? GlobalSearchScope.projectScope(myProject) : GlobalSearchScopesCore.projectProductionScope(myProject);
      case VIRTUAL_FILES -> new GlobalSearchScope() {
        @Override
        public boolean contains(@NotNull VirtualFile file) {
          return getFileSet().contains(file);
        }

        @Override
        public boolean isSearchInModuleContent(@NotNull Module aModule) {
          return false;
        }

        @Override
        public boolean isSearchInLibraries() {
          return false;
        }
      };
      default -> {
        LOG.error("invalid type " + myType);
        yield LocalSearchScope.EMPTY;
      }
    };
  }

  /**
   * @return true iff the scope covers the whole project so no external (non-scope) usages of project's code is possible
   */
  public boolean isTotalScope() {
    return getScopeType() == PROJECT && isIncludeTestSource();
  }
  
  public boolean isIncludeTestSource() {
    return myIncludeTestSource;
  }

  public boolean isAnalyzeInjectedCode() {
    return myAnalyzeInjectedCode;
  }

  public void setFilter(@NotNull GlobalSearchScope filter) {
    myFilter = filter;
  }

  @ApiStatus.Internal
  public @Nullable GlobalSearchScope getFilter() {
    return myFilter;
  }

  @Override
  public String toString() {
    return ReadAction.compute(() -> toSearchScope().toString());
  }
}

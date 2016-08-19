/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.analysis;

import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author max
 */
public class AnalysisScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.analysis.AnalysisScope");

  public static final int PROJECT = 1;
  public static final int DIRECTORY = 2;
  public static final int FILE = 3;
  public static final int MODULE = 4;
  public static final int INVALID = 6;
  public static final int MODULES = 7;
  public static final int CUSTOM = 8;
  public static final int VIRTUAL_FILES = 9;
  public static final int UNCOMMITTED_FILES = 10;
  @MagicConstant(intValues = {PROJECT, DIRECTORY, FILE, MODULE, INVALID, MODULES, CUSTOM, VIRTUAL_FILES, UNCOMMITTED_FILES})
  public @interface Type { }

  @NotNull
  private final Project myProject;
  protected List<Module> myModules;
  protected Module myModule;
  protected PsiElement myElement;
  private SearchScope myScope;
  private boolean mySearchInLibraries;
  private GlobalSearchScope myFilter;
  @Type protected int myType;

  private final Set<VirtualFile> myVFiles;  // initial files and directories the scope is configured on
  protected Set<VirtualFile> myFilesSet;    // set of files (not directories) this scope consists of. calculated in initFilesSet()

  protected boolean myIncludeTestSource = true;

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

  public AnalysisScope(@NotNull Module[] modules) {
    myModules = Arrays.asList(modules);
    myModule = null;
    myProject = modules[0].getProject();
    myElement = null;
    myScope = null;
    myType = MODULES;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull PsiDirectory psiDirectory) {
    myProject = psiDirectory.getProject();
    myModules = null;
    myModule = null;
    myScope = null;
    myElement = psiDirectory;
    myType = DIRECTORY;
    myVFiles = null;
  }

  public AnalysisScope(@NotNull PsiFile psiFile) {
    myProject = psiFile.getProject();
    myElement = psiFile;
    myModule = null;
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

  public AnalysisScope(@NotNull Project project, @NotNull Collection<VirtualFile> virtualFiles) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = null;
    myVFiles = new HashSet<>(virtualFiles);
    myType = VIRTUAL_FILES;
  }

  public void setSearchInLibraries(final boolean searchInLibraries) {
    mySearchInLibraries = searchInLibraries;
  }

  public void setIncludeTestSource(final boolean includeTestSource) {
    myIncludeTestSource = includeTestSource;
  }

  @NotNull
  protected PsiElementVisitor createFileSearcher() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(AnalysisScopeBundle.message("scanning.scope.progress.title"));
    }

    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile file) {
        if (mySearchInLibraries || !(file instanceof PsiCompiledElement)) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile == null) return;
          if (isFiltered(virtualFile)) {
            return;
          }
          if (!shouldHighlightFile(file)) return;
          myFilesSet.add(virtualFile);
        }
      }
    };
  }

  private boolean isFiltered(VirtualFile virtualFile) {
    if (myFilter != null && !myFilter.contains(virtualFile)) {
      return true;
    }
    return !myIncludeTestSource && TestSourcesFilter.isTestSources(virtualFile, myProject);
  }

  @NotNull
  private FileIndex getFileIndex() {
    final FileIndex fileIndex;
    if (myModule != null) {
      fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
    }
    else {
      fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    }
    return fileIndex;
  }

  private static String displayProjectRelativePath(@NotNull PsiFileSystemItem item) {
    VirtualFile virtualFile = item.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    return ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), item.getProject(), true, false);
  }

  public boolean contains(@NotNull PsiElement psiElement) {
    VirtualFile file = psiElement.getContainingFile().getVirtualFile();
    return file != null && contains(file);
  }

  public boolean contains(@NotNull VirtualFile file) {
    if (myFilesSet == null) {
      if (myType == CUSTOM) {
        // optimization
        if (myScope instanceof GlobalSearchScope) return ((GlobalSearchScope)myScope).contains(file);
        if (myScope instanceof LocalSearchScope) return ((LocalSearchScope)myScope).isInScope(file);
      }
      if (myType == PROJECT) {  //optimization
        final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        return index.isInContent(file) && !isFiltered(file);
      }
      initFilesSet();
    }

    return myFilesSet.contains(file);
  }

  protected void initFilesSet() {
    if (myType == FILE) {
      myFilesSet = new HashSet<>(1);
      myFilesSet.add(((PsiFileSystemItem)myElement).getVirtualFile());
    }
    else if (myType == DIRECTORY || myType == PROJECT || myType == MODULES || myType == MODULE || myType == CUSTOM) {
      myFilesSet = new HashSet<>();
      accept(createFileSearcher(), false);
    }
    else if (myType == VIRTUAL_FILES) {
      myFilesSet = new HashSet<>();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (Iterator<VirtualFile> iterator = myVFiles.iterator(); iterator.hasNext(); ) {
        final VirtualFile vFile = iterator.next();
        VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
          @NotNull
          @Override
          public Result visitFileEx(@NotNull VirtualFile file) {
            boolean ignored = fileIndex.isExcluded(file);
            if (!ignored && !file.isDirectory()) {
              myFilesSet.add(file);
            }
            return ignored ? SKIP_CHILDREN : CONTINUE;
          }
        });

        if (vFile.isDirectory()) {
          iterator.remove();
        }
      }
    }
  }


  public void accept(@NotNull final PsiElementVisitor visitor) {
    accept(visitor, true);
  }

  private void accept(@NotNull final PsiElementVisitor visitor, final boolean clearResolveCache) {
    final boolean needReadAction = !ApplicationManager.getApplication().isReadAccessAllowed();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final FileIndex fileIndex = getFileIndex();
    accept(file -> {
      if (file.isDirectory()) return true;
      if (ProjectCoreUtil.isProjectOrWorkspaceFile(file)) return true;
      if (fileIndex.isInContent(file) && !isFiltered(file)
          && !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
        return processFile(file, visitor, psiManager, needReadAction, clearResolveCache);
      }
      return true;
    });
  }

  public boolean accept(@NotNull final Processor<VirtualFile> processor) {
    if (myType == VIRTUAL_FILES) {
      if (myFilesSet == null) initFilesSet();
      for (final VirtualFile file : myFilesSet) {
        if (isFiltered(file)) continue;
        if (!processor.process(file)) return false;
      }
      return true;
    }
    final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (myScope instanceof GlobalSearchScope) {
      final ContentIterator contentIterator = createScopeIterator(processor, myScope);
      if (!projectFileIndex.iterateContent(contentIterator)) return false;
      if (mySearchInLibraries) {
        final VirtualFile[] libraryRoots = LibraryUtil.getLibraryRoots(myProject, false, false);
        for (VirtualFile libraryRoot : libraryRoots) {
          if (!VfsUtilCore.iterateChildrenRecursively(libraryRoot, VirtualFileFilter.ALL, contentIterator)) return false;
        }
      }
      return true;
    }
    if (myScope instanceof LocalSearchScope) {
      final PsiElement[] psiElements = ((LocalSearchScope)myScope).getScope();
      final Set<VirtualFile> files = new THashSet<>();
      for (final PsiElement element : psiElements) {
        VirtualFile file = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
          @Override
          public VirtualFile compute() {
            return PsiUtilCore.getVirtualFile(element);
          }
        });
        if (file != null && files.add(file)) {
          if (!processor.process(file)) return false;
        }
      }
      return true;
    }
    List<Module> modules = myModule != null ? Collections.singletonList(myModule) : myModules;
    if (modules != null) {
      for (final Module module : modules) {
        final FileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
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
      VirtualFile file = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          return PsiUtilCore.getVirtualFile(myElement);
        }
      });
      return file == null || processor.process(file);
    }

    return projectFileIndex.iterateContent(createScopeIterator(processor, null));
  }

  @NotNull
  private ContentIterator createScopeIterator(@NotNull final Processor<VirtualFile> processor, 
                                              @Nullable final SearchScope searchScope) {
    return new ContentIterator() {
        @Override
        public boolean processFile(@NotNull final VirtualFile fileOrDir) {
          final boolean isInScope = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              if (isFiltered(fileOrDir)) return false;
              if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(fileOrDir, myProject)) return false;
              return searchScope == null || ((GlobalSearchScope)searchScope).contains(fileOrDir);
            }
          }).booleanValue();
          return !isInScope || processor.process(fileOrDir);
        }
      };
  }

  private static boolean processFile(@NotNull final VirtualFile vFile,
                                     @NotNull final PsiElementVisitor visitor,
                                     @NotNull final PsiManager psiManager,
                                     final boolean needReadAction, 
                                     final boolean clearResolveCache) {
    final Runnable runnable = () -> doProcessFile(visitor, psiManager, vFile, clearResolveCache);
    if (needReadAction && !ApplicationManager.getApplication().isDispatchThread()) {
      commitAndRunInSmartMode(runnable, psiManager.getProject());
    }
    else {
      runnable.run();
    }
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return indicator == null || !indicator.isCanceled();
  }

  private static void commitAndRunInSmartMode(final Runnable runnable, final Project project) {
    while (true) {
      final DumbService dumbService = DumbService.getInstance(project);
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

  protected static boolean shouldHighlightFile(@NotNull PsiFile file) {
    return ProblemHighlightFilter.shouldProcessFileInBatch(file);
  }

  public boolean containsModule(@NotNull Module module) {
    switch (myType) {
      case PROJECT:
        return true;
      case MODULE:
        return myModule == module;
      case MODULES:
        return myModules.contains(module);
      default:
        return false;
    }
  }

  private static void doProcessFile(@NotNull PsiElementVisitor visitor, @NotNull PsiManager psiManager, @NotNull VirtualFile vFile,
                                    boolean clearResolveCache) {
    if (!vFile.isValid()) return;

    PsiFile psiFile = psiManager.findFile(vFile);
    if (psiFile == null || !shouldHighlightFile(psiFile)) return;

    psiFile.accept(visitor);
    if (clearResolveCache) {
      psiManager.dropResolveCaches();
      InjectedLanguageManager.getInstance(psiManager.getProject()).dropFileCaches(psiFile);
    }
  }

  protected boolean accept(@NotNull final PsiDirectory dir, @NotNull final Processor<VirtualFile> processor) {
    final Project project = dir.getProject();
    //we should analyze generated source files only if the action is explicitly invoked for a directory located under generated roots
    final boolean processGeneratedFiles = GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(dir.getVirtualFile(), project);
    return VfsUtilCore.iterateChildrenRecursively(dir.getVirtualFile(), VirtualFileFilter.ALL, new ContentIterator() {
      @Override
      @SuppressWarnings({"SimplifiableIfStatement"})
      public boolean processFile(@NotNull final VirtualFile fileOrDir) {
        if (isFiltered(fileOrDir)) return true;
        if (!processGeneratedFiles && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(fileOrDir, project)) return true;
        if (!fileOrDir.isDirectory()) {
          return processor.process(fileOrDir);
        }
        return true;
      }
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

  @NotNull
  public String getDisplayName() {
    switch (myType) {
      case CUSTOM:
        return myScope.getDisplayName();

      case MODULE:
        return AnalysisScopeBundle.message("scope.option.module", pathToName(myModule.getModuleFilePath()));

      case MODULES:
        String modules = StringUtil.join(myModules, module -> pathToName(module.getModuleFilePath()), ", ");

        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", myProject.getName());

      case FILE:
        return AnalysisScopeBundle.message("scope.file", displayProjectRelativePath((PsiFileSystemItem)myElement));
      case DIRECTORY:
        return AnalysisScopeBundle.message("scope.directory", displayProjectRelativePath((PsiFileSystemItem)myElement));

      case VIRTUAL_FILES:
        return AnalysisScopeBundle.message("scope.virtual.files");
    }

    return "";
  }

  @NotNull
  public String getShortenName(){
    switch (myType) {
      case CUSTOM:
        return myScope.getDisplayName();

      case MODULE:
        return AnalysisScopeBundle.message("scope.option.module", myModule.getName());

      case MODULES:
        String modules = StringUtil.join(myModules, Module::getName, ", ");
        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", myProject.getName());

      case FILE:
        final String relativePath = getRelativePath();
        return AnalysisScopeBundle.message("scope.file", relativePath);

      case DIRECTORY:
        final String relativeDirPath = getRelativePath();
        return AnalysisScopeBundle.message("scope.directory", relativeDirPath);

      case VIRTUAL_FILES:
        return AnalysisScopeBundle.message("scope.selected.files");
    }

    return "";
  }

  @Nullable
  private String getRelativePath() {
    final String relativePath = displayProjectRelativePath((PsiFileSystemItem)myElement);
    if (relativePath.length() > 100) {
      return ((PsiFileSystemItem)myElement).getName();
    }
    return relativePath;
  }

  @NotNull
  private static String pathToName(@NotNull String path) {
    File file = new File(path);
    return FileUtil.getNameWithoutExtension(file);
  }

  public int getFileCount() {
    if (myFilesSet == null) initFilesSet();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) { //clear text after building analysis scope set
      indicator.setText("");
      indicator.setText2("");
    }
    return myFilesSet.size();
  }

  public void invalidate(){
    if (myType == VIRTUAL_FILES) {
      for (Iterator<VirtualFile> i = myVFiles.iterator(); i.hasNext();) {
        final VirtualFile virtualFile = i.next();
        if (virtualFile == null || !virtualFile.isValid()) {
          i.remove();
        }
      }
    }
    else {
      myFilesSet = null;
    }
  }

  public boolean containsSources(boolean isTest) {
    if (myElement != null) {
      final Project project = myElement.getProject();
      final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (myElement instanceof PsiDirectory) {
        final VirtualFile directory = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (index.isInSourceContent(directory)) {
          return isTest == TestSourcesFilter.isTestSources(directory, myProject);
        }
      } else if (myElement instanceof PsiFile) {
        final VirtualFile file = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (file != null) {
          return isTest == TestSourcesFilter.isTestSources(file, myProject);
        }
      }
    }
    return true;
  }

  @NotNull
  public AnalysisScope getNarrowedComplementaryScope(@NotNull Project defaultProject) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<>();
    if (myType == FILE || myType == DIRECTORY) {
      final VirtualFile vFile = ((PsiFileSystemItem)myElement).getVirtualFile();
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

  @NotNull
  protected static AnalysisScope collectScopes(@NotNull final Project defaultProject, @NotNull final HashSet<Module> modules) {
    if (modules.isEmpty()) {
      return new AnalysisScope(defaultProject);
    }
    final Module[] allModules = ModuleManager.getInstance(defaultProject).getModules();
    Set<Module> modulesToAnalyze = new HashSet<>();
    for (final Module module : modules) {
      modulesToAnalyze.addAll(getDirectBackwardDependencies(module, allModules));
      modulesToAnalyze.addAll(getExportBackwardDependencies(module, allModules));
      modulesToAnalyze.add(module);
    }
    return new AnalysisScope(modulesToAnalyze.toArray(new Module[modulesToAnalyze.size()]));
  }

  @NotNull
  private static Set<Module> getExportBackwardDependencies(@NotNull Module fromModule, @NotNull Module[] allModules) {
    Set<Module> result = new HashSet<>();
    for (Module module : allModules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof ModuleOrderEntry && ((ExportableOrderEntry)orderEntry).isExported() &&
            fromModule == ((ModuleOrderEntry)orderEntry).getModule()) {
          result.addAll(getDirectBackwardDependencies(module, allModules));
        }
      }
    }
    return result;
  }

  @NotNull
  private static Set<Module> getDirectBackwardDependencies(@NotNull Module module, @NotNull Module[] allModules) {
    Set<Module> result = new HashSet<>();
    for (Module dependency : allModules) {
      if (ArrayUtil.find(ModuleRootManager.getInstance(dependency).getDependencies(), module) > -1) {
        result.add(dependency);
      }
    }
    return result;
  }

  @NotNull
  protected static HashSet<Module> getAllInterestingModules(@NotNull final ProjectFileIndex fileIndex, @NotNull final VirtualFile vFile) {
    final HashSet<Module> modules = new HashSet<>();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(vFile)) {
        modules.add(orderEntry.getOwnerModule());
      }
    }
    else {
      modules.add(fileIndex.getModuleForFile(vFile));
    }
    return modules;
  }

  @NotNull
  public SearchScope toSearchScope() {
    switch (myType) {
      case CUSTOM:
        return myScope;
      case DIRECTORY:
        return GlobalSearchScopesCore.directoryScope((PsiDirectory)myElement, true);
      case FILE:
        return new LocalSearchScope(myElement);
      case INVALID:
        return LocalSearchScope.EMPTY;
      case MODULE:
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(myModule);
        return myIncludeTestSource ? moduleScope : GlobalSearchScope.notScope(GlobalSearchScopesCore.projectTestScope(myModule.getProject())).intersectWith(moduleScope);
      case MODULES:
        SearchScope scope = GlobalSearchScope.EMPTY_SCOPE;
        for (Module module : myModules) {
          scope = scope.union(GlobalSearchScope.moduleScope(module));
        }
        return scope;
      case PROJECT:
        return myIncludeTestSource ? GlobalSearchScope.projectScope(myProject) : GlobalSearchScopesCore.projectProductionScope(myProject);
      case VIRTUAL_FILES:
        return new GlobalSearchScope() {
          @Override
          public boolean contains(@NotNull VirtualFile file) {
            return myFilesSet.contains(file);
          }

          @Override
          public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
            return 0;
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
      default:
        LOG.error("invalid type " + myType);
        return GlobalSearchScope.EMPTY_SCOPE;
    }
  }

  public boolean isAnalyzeTestsByDefault() {
    switch (myType) {
      case DIRECTORY:
        return TestSourcesFilter.isTestSources(((PsiDirectory)myElement).getVirtualFile(), myElement.getProject());
      case FILE:
        final PsiFile containingFile = myElement.getContainingFile();
        return TestSourcesFilter.isTestSources(containingFile.getVirtualFile(), containingFile.getProject());
      case MODULE:
        return isTestOnly(myModule);
      case MODULES:
        for (Module module : myModules) {
          if (!isTestOnly(module)) return false;
        }
        return true;

    }
    return false;
  }

  private static boolean isTestOnly(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getSourceRootUrls(false).length == 0;
  }

  public boolean isIncludeTestSource() {
    return myIncludeTestSource;
  }

  public void setFilter(GlobalSearchScope filter) {
    myFilter = filter;
  }
}

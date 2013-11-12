/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
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

  private final Project myProject;
  protected List<Module> myModules;
  protected Module myModule;
  protected PsiElement myElement;
  private SearchScope myScope;
  private boolean mySearchInLibraries = false;
  @Type protected int myType;

  protected Set<VirtualFile> myFilesSet;

  protected boolean myIncludeTestSource = true;

  public AnalysisScope(@NotNull Project project) {
    myProject = project;
    myElement = null;
    myModules = null;
    myModule = null;
    myScope = null;
    myType = PROJECT;
  }

  public AnalysisScope(@NotNull Module module) {
    myProject = null;
    myElement = null;
    myModules = null;
    myScope = null;
    myModule = module;
    myType = MODULE;
  }

  public AnalysisScope(@NotNull Module[] modules) {
    myModules = Arrays.asList(modules);
    myModule = null;
    myProject = null;
    myElement = null;
    myScope = null;
    myType = MODULES;
  }

  public AnalysisScope(@NotNull PsiDirectory psiDirectory) {
    myProject = null;
    myModules = null;
    myModule = null;
    myScope = null;
    myElement = psiDirectory;
    myType = DIRECTORY;
  }

  public AnalysisScope(@NotNull PsiFile psiFile) {
    myProject = null;
    myElement = psiFile;
    myModule = null;
    myModules = null;
    myScope = null;
    myType = FILE;
  }

  public AnalysisScope(@NotNull SearchScope scope, @NotNull Project project) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = scope;
    myType = CUSTOM;
    mySearchInLibraries = scope instanceof GlobalSearchScope && ((GlobalSearchScope)scope).isSearchInLibraries();
  }

  public AnalysisScope(@NotNull Project project, @NotNull Collection<VirtualFile> virtualFiles) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = null;
    myFilesSet = new HashSet<VirtualFile>(virtualFiles);
    myType = VIRTUAL_FILES;
  }

  public void setScope(SearchScope scope) {
    myScope = scope;
  }

  public void setSearchInLibraries(final boolean searchInLibraries) {
    mySearchInLibraries = searchInLibraries;
  }

  public void setIncludeTestSource(final boolean includeTestSource) {
    myIncludeTestSource = includeTestSource;
  }

  @NotNull
  protected PsiElementVisitor createFileSearcher() {
    final FileIndex fileIndex;
    if (myModule != null) {
      fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
    }
    else if (myModules != null && !myModules.isEmpty()) {
      fileIndex = ProjectRootManager.getInstance(myModules.get(0).getProject()).getFileIndex();
    }
    else if (myElement != null) {
      fileIndex = ProjectRootManager.getInstance(myElement.getProject()).getFileIndex();
    }
    else if (myProject != null) {
      fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    }
    else {
      //can't be
      fileIndex = null;
    }
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    return new PsiRecursiveElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile file) {
        if (/*file instanceof PsiJavaFile && */mySearchInLibraries || !(file instanceof PsiCompiledElement)) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile == null) return;
          if (!myIncludeTestSource) {
            if (fileIndex == null || fileIndex.isInTestSourceContent(virtualFile)) {
              return;
            }
          }
          if (!shouldHighlightFile(file)) return;
          myFilesSet.add(virtualFile);
          if (indicator != null) {
            indicator.setText(AnalysisScopeBundle.message("scanning.scope.progress.title"));
            Project project = file.getProject();
            String text = displayProjectRelativePath(virtualFile, project);
            indicator.setText2(text);
          }
        }
      }
    };
  }

  private static String displayProjectRelativePath(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    return ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), project, false, false);
  }

  public boolean contains(@NotNull PsiElement psiElement) {
    return contains(psiElement.getContainingFile().getVirtualFile());
  }

  public boolean contains(VirtualFile file) {
    if (myFilesSet == null) {
      if (myType == CUSTOM) {
        // optimization
        if (myScope instanceof GlobalSearchScope) return ((GlobalSearchScope)myScope).contains(file);
        if (myScope instanceof LocalSearchScope) return ((LocalSearchScope)myScope).isInScope(file);
      }
      if (myType == PROJECT) {  //optimization
        final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        return index.isInContent(file) && (myIncludeTestSource || !index.isInTestSourceContent(file));
      }
      initFilesSet();
    }

    return myFilesSet.contains(file);
  }

  protected void initFilesSet() {
    if (myType == FILE) {
      myFilesSet = new HashSet<VirtualFile>(1);
      myFilesSet.add(((PsiFileSystemItem)myElement).getVirtualFile());
    }
    else if (myType == DIRECTORY || myType == PROJECT || myType == MODULES || myType == MODULE || myType == CUSTOM) {
      myFilesSet = new HashSet<VirtualFile>();
      accept(createFileSearcher());
    }
  }


  public void accept(@NotNull final PsiElementVisitor visitor) {
    accept(visitor, !ApplicationManager.getApplication().isReadAccessAllowed());
  }

  protected void accept(@NotNull final PsiElementVisitor visitor, final boolean needReadAction) {
    if (myType == VIRTUAL_FILES) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final FileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (final VirtualFile file : myFilesSet) {
        if (!myIncludeTestSource && index.isInTestSourceContent(file)) continue;
        if (!processFile(file, visitor, psiManager, needReadAction)) return;
      }
    }
    else if (myScope instanceof GlobalSearchScope) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final ContentIterator contentIterator = new ContentIterator() {
        @Override
        public boolean processFile(@NotNull final VirtualFile fileOrDir) {
          final boolean isInScope = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              if (!myIncludeTestSource && projectFileIndex.isInTestSourceContent(fileOrDir)) return false;
              return ((GlobalSearchScope)myScope).contains(fileOrDir);
            }
          }).booleanValue();
          return !isInScope || AnalysisScope.processFile(fileOrDir, visitor, psiManager, needReadAction);
        }
      };
      projectFileIndex.iterateContent(contentIterator);
      if (mySearchInLibraries) {
        final VirtualFile[] libraryRoots = LibraryUtil.getLibraryRoots(myProject, false, false);
        for (VirtualFile libraryRoot : libraryRoots) {
          VfsUtilCore.iterateChildrenRecursively(libraryRoot, VirtualFileFilter.ALL, contentIterator);
        }
      }
    }
    else if (myScope instanceof LocalSearchScope) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiElement[] psiElements = ((LocalSearchScope)myScope).getScope();
          for (PsiElement element : psiElements) {
            element.accept(visitor);
          }
        }
      });
    }
    else if (myModule != null) {
      final FileIndex moduleFileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
      moduleFileIndex.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(@NotNull VirtualFile fileOrDir) {
          return AnalysisScope.this.processFile(fileOrDir, visitor, moduleFileIndex, psiManager, needReadAction);
        }
      });
    }
    else if (myModules != null) {
      for (final Module module : myModules) {
        final PsiManager psiManager = PsiManager.getInstance(module.getProject());
        final FileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        moduleFileIndex.iterateContent(new ContentIterator() {
          @Override
          public boolean processFile(@NotNull VirtualFile fileOrDir) {
            return AnalysisScope.this.processFile(fileOrDir, visitor, moduleFileIndex, psiManager, needReadAction);
          }
        });
      }
    }
    else if (myElement instanceof PsiDirectory) {
      accept((PsiDirectory)myElement, visitor, needReadAction);
    }
    else if (myElement != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          myElement.accept(visitor);
        }
      });
    }
    else if (myProject != null) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      projectFileIndex.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(@NotNull final VirtualFile fileOrDir) {
          return AnalysisScope.this.processFile(fileOrDir, visitor, projectFileIndex, psiManager, needReadAction);
        }
      });
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private boolean processFile(@NotNull final VirtualFile fileOrDir,
                              @NotNull final PsiElementVisitor visitor,
                              @NotNull final FileIndex projectFileIndex,
                              @NotNull final PsiManager psiManager,
                              final boolean needReadAction) {
    if (fileOrDir.isDirectory()) return true;
    if (ProjectCoreUtil.isProjectOrWorkspaceFile(fileOrDir)) return true;
    if (projectFileIndex.isInContent(fileOrDir) && (myIncludeTestSource || !projectFileIndex.isInTestSourceContent(fileOrDir))) {
      return processFile(fileOrDir, visitor, psiManager, needReadAction);
    }
    return true;
  }

  private static boolean processFile(@NotNull final VirtualFile fileOrDir,
                                     @NotNull final PsiElementVisitor visitor,
                                     @NotNull final PsiManager psiManager,
                                     final boolean needReadAction) {
    if (!fileOrDir.isValid()) return false;
    final PsiFile file = getPsiFileInReadAction(psiManager, fileOrDir);
    if (file == null){
      //skip .class files under src directory
      return true;
    }
    if (!shouldHighlightFile(file)) return true;
    if (needReadAction) {
      PsiDocumentManager.getInstance(psiManager.getProject()).commitAndRunReadAction(new Runnable(){
        @Override
        public void run() {
          doProcessFile(visitor, psiManager, file);
        }
      });
    }
    else {
      doProcessFile(visitor, psiManager, file);
    }
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return indicator == null || !indicator.isCanceled();
  }

  protected static boolean shouldHighlightFile(PsiFile file) {
    return ProblemHighlightFilter.shouldProcessFileInBatch(file);
  }

  public boolean containsModule(Module module) {
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

  private static void doProcessFile(@NotNull PsiElementVisitor visitor, @NotNull PsiManager psiManager, @NotNull PsiFile file) {
    file.accept(visitor);
    psiManager.dropResolveCaches();
    InjectedLanguageManager.getInstance(file.getProject()).dropFileCaches(file);
  }

  protected void accept(@NotNull final PsiDirectory dir, @NotNull final PsiElementVisitor visitor, final boolean needReadAction) {
    final Project project = dir.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VfsUtilCore.iterateChildrenRecursively(dir.getVirtualFile(), VirtualFileFilter.ALL, new ContentIterator() {
      @Override
      @SuppressWarnings({"SimplifiableIfStatement"})
      public boolean processFile(@NotNull final VirtualFile fileOrDir) {
        if (!myIncludeTestSource && index.isInTestSourceContent(fileOrDir)) return true;
        if (!fileOrDir.isDirectory()) {
          return AnalysisScope.processFile(fileOrDir, visitor, psiManager, needReadAction);
        }
        return true;
      }
    });
  }

  public boolean isValid() {
    if (myProject != null) return true;
    if (myModule != null && !myModule.isDisposed()) return true;
    if (myModules != null){
      for (Module module : myModules) {
        if (module.isDisposed()) return false;
      }
      return true;
    }
    return myType == VIRTUAL_FILES || myType == CUSTOM || myElement != null && myElement.isValid();
  }

  @Type
  public int getScopeType() {
    return myType;
  }

  public String getDisplayName() {
    switch (myType) {
      case CUSTOM:
        return myScope.getDisplayName();

      case MODULE:
        return AnalysisScopeBundle.message("scope.option.module", pathToName(myModule.getModuleFilePath()));

      case MODULES:
        String modules = StringUtil.join(myModules, new Function<Module, String>() {
          @Override
          public String fun(@NotNull final Module module) {
            return pathToName(module.getModuleFilePath());
          }
        }, ", ");

        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", myProject.getName());

      case FILE:
        return AnalysisScopeBundle.message("scope.file", getPresentableUrl((PsiFileSystemItem)myElement));

      case DIRECTORY:
        return AnalysisScopeBundle.message("scope.directory", getPresentableUrl((PsiFileSystemItem)myElement));

      case VIRTUAL_FILES:
        return AnalysisScopeBundle.message("scope.virtual.files");
    }

    return "";
  }

  private static String getPresentableUrl(@NotNull final PsiFileSystemItem element) {
    final VirtualFile virtualFile = element.getVirtualFile();
    assert virtualFile != null : element;
    return virtualFile.getPresentableUrl();
  }

  public String getShortenName(){
    switch (myType) {
      case CUSTOM:
        return myScope.getDisplayName();

      case MODULE:
        return AnalysisScopeBundle.message("scope.option.module", myModule.getName());

      case MODULES:
        String modules = StringUtil.join(myModules, new Function<Module, String>() {
          @Override
          @NotNull
          public String fun(@NotNull final Module module) {
            return module.getName();
          }
        }, ", ");
        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", myProject.getName());

      case FILE:
        final String relativePath = getRelativePath();
        return relativePath != null ? AnalysisScopeBundle.message("scope.file", relativePath) : "Current File";

      case DIRECTORY:
        final String relativeDirPath = getRelativePath();
        return relativeDirPath != null ? AnalysisScopeBundle.message("scope.directory", relativeDirPath) : "Current Directory";


      case VIRTUAL_FILES:
        return AnalysisScopeBundle.message("scope.selected.files");
    }

    return "";
  }

  @Nullable
  private String getRelativePath() {
    final String relativePath = displayProjectRelativePath(((PsiFileSystemItem)myElement).getVirtualFile(), myElement.getProject());
    if (relativePath.length() > 100) {
      return null;
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

  public boolean checkScopeWritable(@NotNull Project project) {
    if (myFilesSet == null) initFilesSet();
    return !FileModificationService.getInstance().prepareVirtualFilesForWrite(project, myFilesSet);
  }

  public void invalidate(){
    if (myType != VIRTUAL_FILES) {
      myFilesSet = null;
    } else {
      for (Iterator<VirtualFile> i = myFilesSet.iterator(); i.hasNext();) {
        final VirtualFile virtualFile = i.next();
        if (virtualFile == null || !virtualFile.isValid()) {
          i.remove();
        }
      }
    }
  }

  private static PsiFile getPsiFileInReadAction(@NotNull final PsiManager psiManager, @NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      @Nullable
      public PsiFile compute() {
        if (file.isValid()) {
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null && psiFile.isValid()) {
            return psiFile;
          }
        }
        return null;
      }
    });
  }

  public boolean containsSources(boolean isTest) {
    if (myElement != null) {
      final Project project = myElement.getProject();
      final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (myElement instanceof PsiDirectory) {
        final VirtualFile directory = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (index.isInSourceContent(directory)) {
          return isTest ? index.isInTestSourceContent(directory) : !index.isInTestSourceContent(directory);
        }
      } else if (myElement instanceof PsiFile) {
        final VirtualFile file = ((PsiFileSystemItem)myElement).getVirtualFile();
        if (file != null) {
          return isTest ? index.isInTestSourceContent(file) : !index.isInTestSourceContent(file);
        }
      }
    }
    return true;
  }

  @NotNull
  public AnalysisScope getNarrowedComplementaryScope(@NotNull Project defaultProject) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<Module>();
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
    Set<Module> modulesToAnalyze = new HashSet<Module>();
    for (final Module module : modules) {
      modulesToAnalyze.addAll(getDirectBackwardDependencies(module, allModules));
      modulesToAnalyze.addAll(getExportBackwardDependencies(module, allModules));
      modulesToAnalyze.add(module);
    }
    return new AnalysisScope(modulesToAnalyze.toArray(new Module[modulesToAnalyze.size()]));
  }

  @NotNull
  private static Set<Module> getExportBackwardDependencies(@NotNull Module fromModule, @NotNull Module[] allModules) {
    Set<Module> result = new HashSet<Module>();
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
    Set<Module> result = new HashSet<Module>();
    for (Module dependency : allModules) {
      if (ArrayUtil.find(ModuleRootManager.getInstance(dependency).getDependencies(), module) > -1) {
        result.add(dependency);
      }
    }
    return result;
  }

  @NotNull
  protected static HashSet<Module> getAllInterestingModules(@NotNull final ProjectFileIndex fileIndex, @NotNull final VirtualFile vFile) {
    final HashSet<Module> modules = new HashSet<Module>();
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
        return ProjectRootManager.getInstance(myElement.getProject()).getFileIndex()
          .isInTestSourceContent(((PsiDirectory)myElement).getVirtualFile());
      case FILE:
        final PsiFile containingFile = myElement.getContainingFile();
        return ProjectRootManager.getInstance(myElement.getProject()).getFileIndex().isInTestSourceContent(containingFile.getVirtualFile());
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
}

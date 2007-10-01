/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 *
 */

package com.intellij.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
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
  public static final int PACKAGE = 5;
  public static final int INVALID = 6;
  public static final int MODULES = 7;

  public static final int CUSTOM = 8;
  public static final int VIRTUAL_FILES = 9;

  public static int UNCOMMITED_FILES = 10;

  private final Project myProject;
  private final List<Module> myModules;
  private final Module myModule;
  private final PsiElement myElement;
  private final SearchScope myScope;
  private boolean mySearchInLibraries = false;
  private final int myType;

  private HashSet<VirtualFile> myFilesSet;

  private boolean myIncludeTestSource = true;

  public AnalysisScope(Project project) {
    myProject = project;
    myElement = null;
    myModules = null;
    myModule = null;
    myScope = null;
    myType = PROJECT;
  }

  public AnalysisScope(Module module) {
    myProject = null;
    myElement = null;
    myModules = null;
    myScope = null;
    myModule = module;
    myType = MODULE;
  }

  public AnalysisScope(final Module[] modules) {
    myModules = Arrays.asList(modules);
    myModule = null;
    myProject = null;
    myElement = null;
    myScope = null;
    myType = MODULES;
  }

  public AnalysisScope(PsiDirectory psiDirectory) {
    myProject = null;
    myModules = null;
    myModule = null;
    myScope = null;
    myElement = psiDirectory;
    myType = DIRECTORY;    
  }

  public AnalysisScope(PsiPackage psiPackage) {
    myProject = null;
    myModule = null;
    myModules = null;
    myScope = null;
    myElement = psiPackage;
    myType = PACKAGE;
  }

  public AnalysisScope(PsiFile psiFile) {
    myProject = null;
    myElement = psiFile;
    myModule = null;
    myModules = null;
    myScope = null;
    myType = FILE;
  }

  public AnalysisScope(SearchScope scope, Project project) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = scope;
    myType = CUSTOM;
  }

  public void setSearchInLibraries(final boolean searchInLibraries) {
    mySearchInLibraries = searchInLibraries;
  }

  public AnalysisScope(Project project, Collection<VirtualFile> virtualFiles) {
    myProject = project;
    myElement = null;
    myModule = null;
    myModules = null;
    myScope = null;
    myFilesSet = new HashSet<VirtualFile>(virtualFiles);
    myType = VIRTUAL_FILES;
  }

  public void setIncludeTestSource(final boolean includeTestSource) {
    myIncludeTestSource = includeTestSource;
  }

  private PsiElementVisitor createFileSearcher() {
    final FileIndex fileIndex;
    if (myProject != null){
      fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    } else if (myModule != null){
      fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
    } else if (myModules != null && !myModules.isEmpty()){
      fileIndex = ProjectRootManager.getInstance(myModules.get(0).getProject()).getFileIndex();
    } else if (myElement != null){
      fileIndex = ProjectRootManager.getInstance(myElement.getProject()).getFileIndex();
    } else {
      //can't be
      fileIndex = null;
    }
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    return new PsiRecursiveElementVisitor() {
      public void visitFile(PsiFile file) {
        if (/*file instanceof PsiJavaFile && */mySearchInLibraries || !(file instanceof PsiCompiledElement)) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile == null) return;
          if (!myIncludeTestSource){
            if (fileIndex == null || fileIndex.isInTestSourceContent(virtualFile)){
              return;
            }
          }
          myFilesSet.add(virtualFile);
          if (indicator != null){
            indicator.setText(AnalysisScopeBundle.message("scanning.scope.progress.title"));
            indicator.setText2(VfsUtil.calcRelativeToProjectPath(virtualFile, file.getProject()));
          }
        }
      }
    };
  }

  public boolean contains(PsiElement psiElement) {
    return contains(psiElement.getContainingFile().getVirtualFile());
  }

  public boolean contains(VirtualFile file) {
    if (myFilesSet == null) {
      if (myType == PROJECT) {  //optimization
        final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        return index.isInContent(file) && (myIncludeTestSource || !index.isInTestSourceContent(file));
      }
      initFilesSet();
    }

    return myFilesSet.contains(file);
  }

  private void initFilesSet() {
    if (myType == FILE) {
      myFilesSet = new HashSet<VirtualFile>(1);
      myFilesSet.add(((PsiFileSystemItem)myElement).getVirtualFile());
    }
    else if (myType == DIRECTORY || myType == PROJECT || myType == MODULES || myType == MODULE || myType == PACKAGE || myType == CUSTOM) {
      myFilesSet = new HashSet<VirtualFile>();
      accept(createFileSearcher());
    }
  }

  public AnalysisScope[] getNarrowedComplementaryScope(Project defaultProject) {
    if (myType == PROJECT || myType == CUSTOM) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject)};
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<Module>();
    if (myType == FILE) {
      if (myElement instanceof PsiJavaFile && !PsiUtil.isInJspFile(myElement)) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)myElement;
        final PsiClass[] classes = psiJavaFile.getClasses();
        boolean onlyPackLocalClasses = true;
        for (final PsiClass aClass : classes) {
          if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            onlyPackLocalClasses = false;
          }
        }
        if (onlyPackLocalClasses) {
          final PsiDirectory psiDirectory = psiJavaFile.getContainingDirectory();
          if (psiDirectory != null) {
            return new AnalysisScope[]{new AnalysisScope(psiDirectory.getPackage())};
          }
        }
      }
      final VirtualFile vFile = ((PsiFileSystemItem)myElement).getVirtualFile();
      modules.addAll(getAllInterestingModules(fileIndex, vFile));
    }
    else if (myType == DIRECTORY) {
      final VirtualFile vFile = ((PsiFileSystemItem)myElement).getVirtualFile();
      modules.addAll(getAllInterestingModules(fileIndex, vFile));
    }
    else if (myType == PACKAGE) {
      final PsiDirectory[] directories = ((PsiPackage)myElement).getDirectories();
      for (PsiDirectory directory : directories) {
        modules.addAll(getAllInterestingModules(fileIndex, directory.getVirtualFile()));
      }
    }
    else if (myType == MODULE) {
      modules.add(myModule);
    } else if (myType == MODULES){
      modules.addAll(myModules);
    }

    if (modules.isEmpty()) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject)};
    }
    HashSet<AnalysisScope> result = new HashSet<AnalysisScope>();
    final Module[] allModules = ModuleManager.getInstance(defaultProject).getModules();
    for (final Module module : modules) {
      Set<Module> modulesToAnalyze = getDirectBackwardDependencies(module, allModules);
      modulesToAnalyze.addAll(getExportBackwardDependencies(module, allModules));
      modulesToAnalyze.add(module);
      result.add(new AnalysisScope(modulesToAnalyze.toArray(new Module[modulesToAnalyze.size()])));
    }
    return result.toArray(new AnalysisScope[result.size()]);
  }


  private static Set<Module> getExportBackwardDependencies(Module fromModule, Module [] allModules) {
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

  private static Set<Module> getDirectBackwardDependencies(Module module, Module [] allModules) {
    Set<Module> result = new HashSet<Module>();
    for (Module dependency : allModules) {
      if (ArrayUtil.find(ModuleRootManager.getInstance(dependency).getDependencies(), module) > -1) {
        result.add(dependency);
      }
    }
    return result;
  }

  private static HashSet<Module> getAllInterestingModules(final ProjectFileIndex fileIndex, final VirtualFile vFile) {
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

  public void accept(final PsiElementVisitor visitor) {
    accept(visitor, !ApplicationManager.getApplication().isReadAccessAllowed());
  }

  private void accept(final PsiElementVisitor visitor, final boolean needReadAction) {
    if (myType == VIRTUAL_FILES) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      for (final VirtualFile file : myFilesSet) {
        if (!processFile(file, visitor, psiManager, needReadAction)) return;
      }
    } else if (myScope instanceof GlobalSearchScope) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final ContentIterator contentIterator = new ContentIterator() {
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean processFile(final VirtualFile fileOrDir) {
          if (fileOrDir.isDirectory()) return true;
          final boolean isInScope = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            public Boolean compute() {
              return Boolean.valueOf(((GlobalSearchScope)myScope).contains(fileOrDir));
            }
          }).booleanValue();
          if (isInScope && (myIncludeTestSource || !projectFileIndex.isInTestSourceContent(fileOrDir))) {
            return AnalysisScope.processFile(fileOrDir, visitor, psiManager, needReadAction);
          }
          return true;
        }
      };
      projectFileIndex.iterateContent(contentIterator);
      if (mySearchInLibraries) {
        final VirtualFile[] libraryRoots = LibraryUtil.getLibraryRoots(myProject, false, false);
        for (VirtualFile libraryRoot : libraryRoots) {
          FileIndexImplUtil.iterateRecursively(libraryRoot, VirtualFileFilter.ALL, contentIterator);
        }
      }
    } else if (myScope instanceof LocalSearchScope) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final PsiElement[] psiElements = ((LocalSearchScope)myScope).getScope();
          for (PsiElement element : psiElements) {
            element.accept(visitor);
          }
        }
      });
    } else if (myProject != null) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      projectFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(final VirtualFile fileOrDir) {
          return AnalysisScope.this.processFile(fileOrDir, visitor, projectFileIndex, psiManager, needReadAction);
        }
      });
    }
    else if (myModule != null) {
      final FileIndex moduleFileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
      moduleFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          return AnalysisScope.this.processFile(fileOrDir, visitor, moduleFileIndex, psiManager, needReadAction);
        }
      });
    }
    else if (myModules != null) {
      for (final Module module : myModules) {
        final PsiManager psiManager = PsiManager.getInstance(module.getProject());
        final FileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        moduleFileIndex.iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            return AnalysisScope.this.processFile(fileOrDir, visitor, moduleFileIndex, psiManager, needReadAction);
          }
        });
      }
    }
    else if (myElement instanceof PsiPackage) {
      final PsiPackage pack = (PsiPackage)myElement;
      final Set<PsiDirectory> dirs = new HashSet<PsiDirectory>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          dirs.addAll(Arrays.asList(pack.getDirectories(GlobalSearchScope.projectScope(myElement.getProject()))));
        }
      });
      for (PsiDirectory dir : dirs) {
        accept(dir, visitor, needReadAction);
      }
    }
    else if (myElement instanceof PsiDirectory) {
      accept((PsiDirectory)myElement, visitor, needReadAction);
    }
    else {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          myElement.accept(visitor);
        }
      });
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private boolean processFile(final VirtualFile fileOrDir,
                              final PsiElementVisitor visitor,
                              final FileIndex projectFileIndex,
                              final PsiManager psiManager, final boolean needReadAction) {
    if (fileOrDir.isDirectory()) return true;
    if (projectFileIndex.isInContent(fileOrDir) && (myIncludeTestSource || !projectFileIndex.isInTestSourceContent(fileOrDir))) {
      return processFile(fileOrDir, visitor, psiManager, needReadAction);
    }
    return true;
  }

  private static boolean processFile(final VirtualFile fileOrDir, final PsiElementVisitor visitor, final PsiManager psiManager,
                                     final boolean needReadAction) {
    final PsiFile file = getPsiFileInReadAction(psiManager, fileOrDir);
    if (file == null){
      //skip .class files under src directory
      return true;
    }
    if (needReadAction) {
      PsiDocumentManager.getInstance(psiManager.getProject()).commitAndRunReadAction(new Runnable(){
        public void run() {
          file.accept(visitor);
          psiManager.dropResolveCaches();
        }
      });
    } else {
      file.accept(visitor);
      psiManager.dropResolveCaches();
    }
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return indicator == null || !indicator.isCanceled();
  }

  private void accept(@NotNull final PsiDirectory dir, @NotNull final PsiElementVisitor visitor, final boolean needReadAction) {
    final Project project = dir.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    index.iterateContentUnderDirectory(dir.getVirtualFile(), new ContentIterator() {
      @SuppressWarnings({"SimplifiableIfStatement"})
      public boolean processFile(final VirtualFile fileOrDir) {
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
          public String fun(final Module module) {
            return pathToName(module.getModuleFilePath());
          }
        }, ", ");

        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", pathToName(myProject.getPresentableUrl()));

      case FILE:
        final VirtualFile virtualFile = ((PsiFileSystemItem)myElement).getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        return AnalysisScopeBundle.message("scope.file", virtualFile.getPresentableUrl());

      case DIRECTORY:
        return AnalysisScopeBundle.message("scope.directory", ((PsiFileSystemItem)myElement).getVirtualFile().getPresentableUrl());

      case PACKAGE:
        return AnalysisScopeBundle.message("scope.package", ((PsiPackage)myElement).getQualifiedName());
    }

    return "";
  }

  public String getShortenName(){
    switch (myType) {
      case CUSTOM:
        return myScope.getDisplayName();

      case MODULE:
        return AnalysisScopeBundle.message("scope.option.module", myModule.getName());

      case MODULES:
        String modules = StringUtil.join(myModules, new Function<Module, String>() {
          public String fun(final Module module) {
            return module.getName();
          }
        }, ", ");
        return AnalysisScopeBundle.message("scope.module.list", modules, Integer.valueOf(myModules.size()));

      case PROJECT:
        return AnalysisScopeBundle.message("scope.project", myProject.getName());

      case FILE:
        return AnalysisScopeBundle.message("scope.file", VfsUtil.calcRelativeToProjectPath(((PsiFileSystemItem)myElement).getVirtualFile(), myElement.getProject()));

      case DIRECTORY:
        return AnalysisScopeBundle.message("scope.directory", VfsUtil.calcRelativeToProjectPath(((PsiFileSystemItem)myElement).getVirtualFile(), myElement.getProject()));

      case PACKAGE:
        return AnalysisScopeBundle.message("scope.package", ((PsiPackage)myElement).getQualifiedName());

      case VIRTUAL_FILES:
        return AnalysisScopeBundle.message("scope.selected.files");
    }

    return "";
  }

  private static String pathToName(String path) {
    String name = path;
    if (path != null) {
      File file = new File(path);
      name = FileUtil.getNameWithoutExtension(file);
    }
    return name;
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

  public Set<String> getActiveInspectionProfiles() {
    Set<String> result = new HashSet<String>();
    if (myType == PROJECT || myType == CUSTOM){
      final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(myProject, Profile.INSPECTION);
      LOG.assertTrue(profileManager != null);
      if (profileManager.useProjectLevelProfileSettings()) {
        result.addAll(profileManager.getProfilesUsedInProject().values());
        result.add(profileManager.getProjectProfile());
      } else {
        final ApplicationProfileManager applicationProfileManager = ApplicationProfileManager.getProfileManager(Profile.INSPECTION);
        LOG.assertTrue(applicationProfileManager != null);
        result.add(applicationProfileManager.getRootProfile().getName());
      }
    } else if (myType == MODULE){
      processModule(result, myModule);
    } else if (myType == MODULES){
      for (Module module : myModules) {
        processModule(result, module);
      }
    } else if (myType == FILE){
      final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(myElement.getProject(), Profile.INSPECTION);
      LOG.assertTrue(profileManager != null);
      result.add(profileManager.getProfileName((PsiFile)myElement));
    } else if (myType == DIRECTORY) {
      final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(myElement.getProject(), Profile.INSPECTION);
      LOG.assertTrue(profileManager != null);
      processDirectories(new PsiDirectory[]{(PsiDirectory)myElement}, result, profileManager);
    } else if (myType == PACKAGE){
      final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(myElement.getProject(), Profile.INSPECTION);
      LOG.assertTrue(profileManager != null);
      final PsiDirectory[] psiDirectories = ((PsiPackage)myElement).getDirectories();
      processDirectories(psiDirectories, result, profileManager);
    } else if (myType == VIRTUAL_FILES){
      final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(myProject, Profile.INSPECTION);
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      LOG.assertTrue(profileManager != null);
      for (final VirtualFile file : myFilesSet) {
        final PsiFile psiFile = getPsiFileInReadAction(psiManager, file);
        if (psiFile != null && psiFile.isValid()) {
          result.add(profileManager.getProfileName(psiFile));
        }
      }
    }
    return result;
  }

  private static PsiFile getPsiFileInReadAction(final PsiManager psiManager, final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Nullable
      public PsiFile compute() {
        return psiManager.findFile(file);
      }
    });
  }

  private static void processDirectories(final PsiDirectory[] psiDirectories,
                                         final Set<String> result,
                                         final ProjectProfileManager profileManager) {
    for (final PsiDirectory directory : psiDirectories) {
      final PsiFile[] psiFiles = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
        public PsiFile[] compute() {
          return directory.getFiles();
        }
      });
      for (PsiFile file : psiFiles) {
        result.add(profileManager.getProfileName(file));
      }
      processDirectories(ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory[]>() {
        public PsiDirectory[] compute() {
          return directory.getSubdirectories();
        }
      }), result, profileManager);
    }
  }

  private static void processModule(final Set<String> result, final Module module) {
    final Project project = module.getProject();
    final ProjectProfileManager profileManager = ProjectProfileManager.getProjectProfileManager(project, Profile.INSPECTION);
    LOG.assertTrue(profileManager != null);
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    final PsiDirectory[] dirs = new PsiDirectory[files.length];
    final PsiManager psiManager = PsiManager.getInstance(project);
    int i = 0;
    for (final VirtualFile file : files) {
      dirs[i++] = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
        @Nullable
        public PsiDirectory compute() {
          return psiManager.findDirectory(file);
        }
      });
    }
    processDirectories(dirs, result, profileManager);
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
}

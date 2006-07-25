/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class GlobalSearchScope extends SearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.GlobalSearchScope");

  public abstract boolean contains(VirtualFile file);

  /**
   * @param file1
   * @param file2
   * @return a positive integer (+1), if file1 is located in the classpath before file2,
   *         a negative integer (-1), if file1 is located in the classpath after file2
   *         zero - otherwise or when the file are not comparable.
   */
  public abstract int compare(VirtualFile file1, VirtualFile file2);

  // optimization methods:

  public abstract boolean isSearchInModuleContent(Module aModule);

  public abstract boolean isSearchInLibraries();

  @NotNull public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    return intersection(this, scope);
  }

  private static GlobalSearchScope intersection(final GlobalSearchScope scope1, final GlobalSearchScope scope2) {
    return new IntersectionScope(scope1, scope2, null);
  }

  private static GlobalSearchScope union(final GlobalSearchScope scope1, final GlobalSearchScope scope2) {
    return new UnionScope(scope1, scope2, null);
  }
  public GlobalSearchScope uniteWith(final GlobalSearchScope scope) {
    return union(this, scope);
  }

  public static GlobalSearchScope allScope(final Project project) {
    return project.getAllScope();
  }

  public static GlobalSearchScope projectScope(Project project) {
    return project.getProjectScope();
  }

  public static GlobalSearchScope projectProductionScope(Project project, final boolean includeNonJavaFiles) {
    return new IntersectionScope(projectScope(project),
                                 new ProductionScopeFilter(project, includeNonJavaFiles),
                                 PsiBundle.message("psi.search.scope.production.files"));
  }

  public static GlobalSearchScope projectTestScope(Project project, final boolean includeNonJavaFiles) {
    return new IntersectionScope(projectScope(project),
                                 new TestScopeFilter(project, includeNonJavaFiles),
                                 PsiBundle.message("psi.search.scope.test.files"));
  }

  public static GlobalSearchScope filterScope(Project project, NamedScope set, final boolean includeNonJavaFiles) {
    return new FilterScopeAdapter(project, set, includeNonJavaFiles);
  }

  public static GlobalSearchScope moduleScope(Module module) {
    return module.getModuleScope();
  }

  public static GlobalSearchScope moduleWithLibrariesScope(Module module) {
    return module.getModuleWithLibrariesScope();
  }

  public static GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  public static GlobalSearchScope moduleRuntimeScope(Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependentsScope(Module module) {
    return module.getModuleWithDependentsScope();
  }

  public static GlobalSearchScope moduleTestsWithDependentsScope(Module module) {
    return module.getModuleWithDependentsScope();
  }

  public static GlobalSearchScope packageScope(PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, true);
  }

  public static GlobalSearchScope directoryScope(PsiDirectory directory, final boolean withSubdirectories) {
    return new DirectoryScope(directory, withSubdirectories);
  }

  public static GlobalSearchScope packageScopeWithoutLibraries(PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, false);
  }

  public static SearchScope fileScope(@NotNull PsiFile psiFile) {
    return new FileScope(psiFile);
  }

  private static class IntersectionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private String myDisplayName;

    public IntersectionScope(GlobalSearchScope scope1, GlobalSearchScope scope2, String displayName) {
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
      LOG.assertTrue(myScope1 != null);
      LOG.assertTrue(myScope2 != null);
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) && myScope2.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.compare(file1, file2);
      int res2 = myScope2.compare(file1, file2);

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) && myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() && myScope2.isSearchInLibraries();
    }
  }
  private static class UnionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private String myDisplayName;

    public UnionScope(GlobalSearchScope scope1, GlobalSearchScope scope2, String displayName) {
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
      LOG.assertTrue(myScope1 != null);
      LOG.assertTrue(myScope2 != null);
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.union", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) || myScope2.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.contains(file1) && myScope1.contains(file2) ? myScope1.compare(file1, file2) : 0;
      int res2 = myScope2.contains(file1) && myScope2.contains(file2) ? myScope2.compare(file1, file2) : 0;

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) || myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() || myScope2.isSearchInLibraries();
    }
  }

  private static class ProductionScopeFilter extends GlobalSearchScope {
    private ProjectFileIndex myFileIndex;
    private boolean myIncludeNonJavaFiles;

    public ProductionScopeFilter(Project project, boolean includeNonJavaFiles) {
      myIncludeNonJavaFiles = includeNonJavaFiles;
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      if (!myFileIndex.isJavaSourceFile(file)) return myIncludeNonJavaFiles;
      return myFileIndex.isInSourceContent(file) && !myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class TestScopeFilter extends GlobalSearchScope {
    private ProjectFileIndex myFileIndex;
    private boolean myIncludeNonJavaFiles;

    public TestScopeFilter(Project project, boolean includeNonJavaFiles) {
      myIncludeNonJavaFiles = includeNonJavaFiles;
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      if (!myFileIndex.isJavaSourceFile(file)) return myIncludeNonJavaFiles;
      return myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class PackageScope extends GlobalSearchScope {
    private final Collection<VirtualFile> myDirs;
    private final PsiPackage myPackage;
    private final boolean myIncludeSubpackages;
    private final boolean myIncludeLibraries;

    public PackageScope(PsiPackage aPackage, boolean includeSubpackages, final boolean includeLibraries) {
      myPackage = aPackage;
      myIncludeSubpackages = includeSubpackages;

      Project project = myPackage.getProject();
      FileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      myDirs = fileIndex.getDirsByPackageName(myPackage.getQualifiedName(), true).findAll();
      myIncludeLibraries = includeLibraries;
    }

    public boolean contains(VirtualFile file) {
      for (VirtualFile scopeDir : myDirs) {
        boolean inDir = myIncludeSubpackages
                        ? VfsUtil.isAncestor(scopeDir, file, false)
                        : Comparing.equal(file.getParent(), scopeDir);
        if (inDir) return true;
      }
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "package scope: " + myPackage +
             ", includeSubpackages = " + myIncludeSubpackages;
    }
  }
  private static class DirectoryScope extends GlobalSearchScope {
    private final VirtualFile myDirectory;
    private final boolean myWithSubdirectories;

    public DirectoryScope(PsiDirectory directory, final boolean withSubdirectories) {
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory.getVirtualFile();
    }

    public boolean contains(VirtualFile file) {
      if (myWithSubdirectories) {
        return VfsUtil.isAncestor(myDirectory, file, false);
      }
      else {
        return myDirectory.equals(file.getParent());
      }
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "directory scope: " + myDirectory + "; withSubdirs:"+myWithSubdirectories;
    }
  }

  private static class FileScope extends GlobalSearchScope {
    private final VirtualFile myVirtualFile;
    private final Module myModule;

    public FileScope(PsiFile psiFile) {
      myVirtualFile = psiFile.getVirtualFile();
      Project project = psiFile.getProject();
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      myModule = myVirtualFile != null ? fileIndex.getModuleForFile(myVirtualFile) : null;
    }

    public boolean contains(VirtualFile file) {
      return Comparing.equal(myVirtualFile, file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return aModule == myModule;
    }

    public boolean isSearchInLibraries() {
      return myModule == null;
    }
  }

  private static class FilterScopeAdapter extends GlobalSearchScope {
    private final NamedScope mySet;
    private boolean myIncludeNonJavaFiles;
    private final PsiManager myManager;
    private Project myProject;

    public FilterScopeAdapter(Project project, NamedScope set, boolean includeNonJavaFiles) {
      mySet = set;
      myIncludeNonJavaFiles = includeNonJavaFiles;
      myProject = project;
      myManager = PsiManager.getInstance(myProject);
    }

    public boolean contains(VirtualFile file) {
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile == null) return false;
      NamedScopesHolder holder = NamedScopeManager.getInstance(myProject);
      final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
      final PackageSet packageSet = mySet.getValue();
      if (psiFile instanceof PsiJavaFile || (containingDirectory != null && containingDirectory.getPackage() != null)) {
        return packageSet != null && packageSet.contains(psiFile, holder);
      }
      else {
        return myIncludeNonJavaFiles;
      }
    }

    public String getDisplayName() {
      return mySet.getName();
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;

    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true; //TODO (optimization?)
    }

    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }
  }

  public static GlobalSearchScope getScopeRestrictedByFileTypes (final GlobalSearchScope scope, final FileType... fileTypes) {
    LOG.assertTrue(fileTypes.length > 0);
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  private static class FileTypeRestrictionScope extends GlobalSearchScope {
    private GlobalSearchScope myScope;
    private FileType[] myFileTypes;

    public FileTypeRestrictionScope(final GlobalSearchScope scope, final FileType[] fileTypes) {
      myFileTypes = fileTypes;
      myScope = scope;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return myScope.compare(file1, file2);
    }

    public boolean contains(VirtualFile file) {
      if (!myScope.contains(file)) return false;

      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
      for (FileType otherFileType : myFileTypes) {
        if (fileType.equals(otherFileType)) return true;
      }

      return false;
    }

    public boolean isSearchInLibraries() {
      return myScope.isSearchInLibraries();
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return myScope.isSearchInModuleContent(aModule);
    }
  }
}

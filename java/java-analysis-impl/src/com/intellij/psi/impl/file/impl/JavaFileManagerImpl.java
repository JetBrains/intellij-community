// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PackagePrefixElementFinder;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.JavaModuleSearch;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * @author dmitry lomov
 */
public final class JavaFileManagerImpl implements JavaFileManager, Disposable {
  private static final Logger LOG = Logger.getInstance(JavaFileManagerImpl.class);

  private final PsiManagerEx myManager;
  private boolean myDisposed;

  public JavaFileManagerImpl(Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @Override
  public @Nullable PsiPackage findPackage(@NotNull String packageName) {
    PackageIndex index = PackageIndex.getInstance(myManager.getProject());
    if (index.getDirsByPackageName(packageName, true).findFirst() == null &&
        index.getFilesByPackageName(packageName).findFirst() == null) {
      return null;
    }
    return new PsiPackageImpl(myManager, packageName);
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    List<Pair<PsiClass, VirtualFile>> result = doFindClasses(qName, scope);

    int count = result.size();
    if (count == 0) return PsiClass.EMPTY_ARRAY;
    if (count == 1) return new PsiClass[] {result.getFirst().getFirst()};

    ContainerUtil.quickSort(result, (o1, o2) -> scope.compare(o2.getSecond(), o1.getSecond()));

    return result.stream().map(p -> p.getFirst()).toArray(PsiClass[]::new);
  }

  @Override
  public boolean hasClass(@NotNull String qName, @NotNull GlobalSearchScope scope, @NotNull Predicate<PsiClass> filter) {
    List<Pair<PsiClass, VirtualFile>> pairs = doFindClasses(qName, scope);
    if (filter == Predicates.<PsiClass>alwaysTrue()) return !pairs.isEmpty();
    for (Pair<PsiClass, VirtualFile> pair : pairs) {
      if (filter.test(pair.getFirst())) {
        return true;
      }
    }
    return false;
  }

  private @NotNull List<Pair<PsiClass, VirtualFile>> doFindClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    Collection<PsiClass> classes = JavaFullClassNameIndex.getInstance().getClasses(qName, myManager.getProject(), scope);
    if (classes.isEmpty()) return Collections.emptyList();

    List<Pair<PsiClass, VirtualFile>> result = new ArrayList<>(classes.size());
    for (PsiClass aClass : classes) {
      PsiFile file = aClass.getContainingFile();
      if (file == null) {
        throw new AssertionError("No file for class: " + aClass + " of " + aClass.getClass());
      }
      VirtualFile vFile = file.getViewProvider().getVirtualFile();
      if (!hasAcceptablePackage(vFile)) continue;

      result.add(Pair.create(aClass, vFile));
    }

    return result;
  }

  @Override
  public @Nullable PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    LOG.assertTrue(!myDisposed);
    VirtualFile bestFile = null;
    PsiClass bestClass = null;
    List<Pair<PsiClass, VirtualFile>> result = doFindClasses(qName, scope);

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < result.size(); i++) {
      Pair<PsiClass, VirtualFile> pair = result.get(i);
      VirtualFile vFile = pair.getSecond();
      if (bestFile == null || scope.compare(vFile, bestFile) > 0) {
        bestFile = vFile;
        bestClass = pair.getFirst();
      }
    }

    return bestClass;
  }

  private boolean hasAcceptablePackage(@NotNull VirtualFile vFile) {
    if (FileTypeRegistry.getInstance().isFileOfType(vFile, JavaClassFileType.INSTANCE)) {
      // See IDEADEV-5626
      ProjectFileIndex index = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
      boolean checkMultiRelease = index.isInLibrary(vFile);
      VirtualFile root = index.getClassRootForFile(vFile);
      VirtualFile parent = vFile.getParent();
      PsiNameHelper nameHelper = PsiNameHelper.getInstance(myManager.getProject());
      while (parent != null && !Comparing.equal(parent, root) &&
             (!checkMultiRelease || JavaMultiReleaseUtil.getVersionForVersionRoot(root, parent) == null)) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  @Override
  public @NotNull Collection<String> getNonTrivialPackagePrefixes() {
    return PackagePrefixElementFinder.getInstance(myManager.getProject()).getAllPackagePrefixes(GlobalSearchScope.projectScope(myManager.getProject()));
  }

  @Override
  public @NotNull Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    GlobalSearchScope excludingScope = new LibSrcExcludingScope(scope);
    Collection<PsiJavaModule> results = JavaModuleSearch.search(moduleName, myManager.getProject(), excludingScope).findAll();
    return upgradeModules(sortModules(results, scope), moduleName, scope);
  }

  private static class LibSrcExcludingScope extends DelegatingGlobalSearchScope {
    private final ProjectFileIndex myIndex;

    LibSrcExcludingScope(@NotNull GlobalSearchScope baseScope) {
      super(baseScope);
      myIndex = ProjectFileIndex.getInstance(requireNonNull(baseScope.getProject()));
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) && (!myIndex.isInLibrarySource(file) || myIndex.isInLibraryClasses(file));
    }
  }

  private static Collection<PsiJavaModule> sortModules(Collection<PsiJavaModule> modules, GlobalSearchScope scope) {
    if (modules.size() > 1) {
      List<PsiJavaModule> list = new ArrayList<>(modules);
      list.sort((m1, m2) -> scope.compare(PsiImplUtil.getModuleVirtualFile(m2), PsiImplUtil.getModuleVirtualFile(m1)));
      modules = list;
    }
    return modules;
  }

  private static Collection<PsiJavaModule> upgradeModules(Collection<PsiJavaModule> modules, String moduleName, GlobalSearchScope scope) {
    if (scope instanceof DelegatingGlobalSearchScope delegatingScope) {
      scope = delegatingScope.unwrap();
    }
    if (modules.size() > 1 && PsiJavaModule.UPGRADEABLE.contains(moduleName) && scope instanceof ModuleWithDependenciesScope moduleScope) {
      Module module = moduleScope.getModule();
      boolean isModular = Stream.of(ModuleRootManager.getInstance(module).getSourceRoots(true))
        .filter(scope::contains)
        .anyMatch(root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE) != null);
      if (isModular) {
        List<PsiJavaModule> list = new ArrayList<>(modules);

        ProjectFileIndex index = ProjectFileIndex.getInstance(module.getProject());
        for (ListIterator<PsiJavaModule> i = list.listIterator(); i.hasNext(); ) {
          PsiJavaModule candidate = i.next();
          if (!index.findContainingSdks(PsiImplUtil.getModuleVirtualFile(candidate)).isEmpty()) {
            if (i.previousIndex() > 0) {
              i.remove();  // not at the top -> is upgraded
            }
            else {
              list = Collections.singletonList(candidate);  // shadows subsequent modules
              break;
            }
          }
        }

        if (list.size() != modules.size()) {
          modules = list;
        }
      }
    }

    return modules;
  }
}

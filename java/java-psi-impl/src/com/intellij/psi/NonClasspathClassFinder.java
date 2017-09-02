/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.PackageDirectoryCache;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class NonClasspathClassFinder extends PsiElementFinder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.NonClasspathClassFinder");
  private static final EverythingGlobalScope ALL_SCOPE = new EverythingGlobalScope();
  protected final Project myProject;
  private volatile PackageDirectoryCache myCache;
  private final PsiManager myManager;
  private final String[] myFileExtensions;

  public NonClasspathClassFinder(@NotNull Project project, @NotNull String... fileExtensions) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    myFileExtensions = ArrayUtil.append(fileExtensions, "class");
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        clearCache();
      }
    });
    LowMemoryWatcher.register(() -> clearCache(), project);
  }

  @NotNull 
  protected PackageDirectoryCache getCache(@Nullable GlobalSearchScope scope) {
    PackageDirectoryCache cache = myCache;
    if (cache == null) {
      List<VirtualFile> roots = calcClassRoots();
      List<VirtualFile> invalidRoots = ContainerUtil.filter(roots, f -> !f.isValid());
      if (!invalidRoots.isEmpty()) {
        roots.removeAll(invalidRoots);
        LOG.error("Invalid roots returned by " + getClass() + ": " + invalidRoots);
      }
      myCache = cache = createCache(roots);
    }
    return cache;
  }

  @NotNull
  protected static PackageDirectoryCache createCache(@NotNull final List<VirtualFile> roots) {
    final MultiMap<String, VirtualFile> map = MultiMap.create();
    map.putValues("", roots);
    return new PackageDirectoryCache(map);
  }

  public void clearCache() {
    myCache = null;
  }
  
  protected List<VirtualFile> getClassRoots(@Nullable GlobalSearchScope scope) {
    return getCache(scope).getDirectoriesByPackageName("");
  }

  public List<VirtualFile> getClassRoots() {
    return getClassRoots(ALL_SCOPE);
  }

  @Override
  public PsiClass findClass(@NotNull final String qualifiedName, @NotNull GlobalSearchScope scope) {
    final Ref<PsiClass> result = Ref.create();
    processDirectories(StringUtil.getPackageName(qualifiedName), scope, dir -> {
      VirtualFile virtualFile = findChild(dir, StringUtil.getShortName(qualifiedName), myFileExtensions);
      final PsiFile file = virtualFile == null ? null : myManager.findFile(virtualFile);
      if (file instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
        if (classes.length == 1) {
          result.set(classes[0]);
          return false;
        }
      }
      return true;
    });
    return result.get();
  }

  protected abstract List<VirtualFile> calcClassRoots();

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<PsiClass> result = ContainerUtil.newArrayList();
    processDirectories(psiPackage.getQualifiedName(), scope, dir -> {
      for (final VirtualFile file : dir.getChildren()) {
        if (!file.isDirectory() && ArrayUtil.contains(file.getExtension(), myFileExtensions)) {
          final PsiFile psi = myManager.findFile(file);
          if (psi instanceof PsiClassOwner) {
            ContainerUtil.addAll(result, ((PsiClassOwner)psi).getClasses());
          }
        }
      }
      return true;
    });
    return result.toArray(new PsiClass[result.size()]);
  }


  @NotNull
  @Override
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final Set<String> result = new HashSet<>();
    processDirectories(psiPackage.getQualifiedName(), scope, dir -> {
      for (final VirtualFile file : dir.getChildren()) {
        if (!file.isDirectory() && ArrayUtil.contains(file.getExtension(), myFileExtensions)) {
          result.add(file.getNameWithoutExtension());
        }
      }
      return true;
    });
    return result;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    final CommonProcessors.FindFirstProcessor<VirtualFile> processor = new CommonProcessors.FindFirstProcessor<>();
    processDirectories(qualifiedName, ALL_SCOPE, processor);
    return processor.getFoundValue() != null ? createPackage(qualifiedName) : null;
  }

  private PsiPackageImpl createPackage(String qualifiedName) {
    return new PsiPackageImpl(myManager, qualifiedName);
  }

  @Override
  public boolean processPackageDirectories(@NotNull final PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull final Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    return processDirectories(psiPackage.getQualifiedName(), scope, dir -> {
      final PsiDirectory psiDirectory = psiPackage.getManager().findDirectory(dir);
      return psiDirectory == null || consumer.process(psiDirectory);
    });
  }

  private boolean processDirectories(@NotNull String qualifiedName,
                                     @NotNull final GlobalSearchScope scope,
                                     @NotNull final Processor<VirtualFile> processor) {
    return ContainerUtil.process(getCache(scope).getDirectoriesByPackageName(qualifiedName),
                                 file -> !scope.contains(file) || processor.process(file));
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final String pkgName = psiPackage.getQualifiedName();
    final Set<String> names = getCache(scope).getSubpackageNames(pkgName);
    if (names.isEmpty()) {
      return super.getSubPackages(psiPackage, scope);
    }

    List<PsiPackage> result = new ArrayList<>();
    for (String name : names) {
      result.add(createPackage(pkgName.isEmpty() ? name : pkgName + "." + name));
    }
    return result.toArray(new PsiPackage[result.size()]);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass psiClass = findClass(qualifiedName, scope);
    return psiClass == null ? PsiClass.EMPTY_ARRAY : new PsiClass[]{psiClass};
  }

  @NotNull
  public static GlobalSearchScope addNonClasspathScope(@NotNull Project project, @NotNull GlobalSearchScope base) {
    GlobalSearchScope scope = base;
    for (PsiElementFinder finder : Extensions.getExtensions(EP_NAME, project)) {
      if (finder instanceof NonClasspathClassFinder) {
        scope = scope.uniteWith(NonClasspathDirectoriesScope.compose(((NonClasspathClassFinder)finder).getClassRoots()));
      }
    }
    return scope;
  }

  public PsiManager getPsiManager() {
    return myManager;
  }

  @Nullable
  private static VirtualFile findChild(@NotNull VirtualFile root,
                                       @NotNull String relPath,
                                       @NotNull String[] extensions) {
    VirtualFile file = null;
    for (String extension : extensions) {
      file = root.findChild(relPath + '.' + extension);
      if (file != null) break;
    }
    return file;
  }
}

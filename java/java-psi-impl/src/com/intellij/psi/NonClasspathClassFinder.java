// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageDirectoryCache;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class NonClasspathClassFinder extends PsiElementFinder {
  private static final Logger LOG = Logger.getInstance(NonClasspathClassFinder.class);
  private static final EverythingGlobalScope ALL_SCOPE = new EverythingGlobalScope();
  protected final Project myProject;
  private volatile PackageDirectoryCache myCache;
  private final PsiManager myManager;
  private final String[] myFileExtensions;

  public NonClasspathClassFinder(@NotNull Project project, String @NotNull ... fileExtensions) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    myFileExtensions = ArrayUtil.append(fileExtensions, "class");
    Disposable extensionDisposable = ExtensionPointUtil.createExtensionDisposable(this, EP.getPoint(project));
    final MessageBusConnection connection = project.getMessageBus().connect(extensionDisposable);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        clearCache();
      }
    });
    LowMemoryWatcher.register(() -> clearCache(), extensionDisposable);
  }

  @NotNull
  protected PackageDirectoryCache getCache(@Nullable GlobalSearchScope scope) {
    PackageDirectoryCache cache = myCache;
    if (cache == null) {
      List<VirtualFile> roots = calcClassRoots();
      List<VirtualFile> invalidRoots = ContainerUtil.filter(roots, f -> !f.isValid());
      if (!invalidRoots.isEmpty()) {
        roots = ContainerUtil.filter(roots, VirtualFile::isValid);
        PluginException.logPluginError(
          LOG,
          "Invalid roots returned by " + getClass().getName() + ": " + invalidRoots,
          null, getClass()
        );
      }
      myCache = cache = PackageDirectoryCache.createCache(roots);
    }
    return cache;
  }

  public void clearCache() {
    myCache = null;
  }

  protected List<VirtualFile> getClassRoots(@Nullable GlobalSearchScope scope) {
    return getCache(scope).getDirectoriesByPackageName("");
  }

  @Contract(pure = true)
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

  @Override
  public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<PsiClass> result = new ArrayList<>();
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
    return result.toArray(PsiClass.EMPTY_ARRAY);
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
                                           @NotNull final Processor<? super PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    return processDirectories(psiPackage.getQualifiedName(), scope, dir -> {
      final PsiDirectory psiDirectory = psiPackage.getManager().findDirectory(dir);
      return psiDirectory == null || consumer.process(psiDirectory);
    });
  }

  private boolean processDirectories(@NotNull String qualifiedName,
                                     @NotNull GlobalSearchScope scope,
                                     @NotNull final Processor<? super VirtualFile> processor) {
    //TODO use some generic approach
    if (scope instanceof EverythingGlobalScope) {
      scope = ALL_SCOPE;
    }
    @NotNull GlobalSearchScope finalScope = scope;
    return ContainerUtil.process(getCache(scope).getDirectoriesByPackageName(qualifiedName),
                                 file -> !finalScope.contains(file) || processor.process(file));
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final String pkgName = psiPackage.getQualifiedName();
    final Set<String> names = getCache(scope).getSubpackageNames(pkgName, scope);
    if (names.isEmpty()) {
      return super.getSubPackages(psiPackage, scope);
    }

    List<PsiPackage> result = new ArrayList<>();
    for (String name : names) {
      result.add(createPackage(pkgName.isEmpty() ? name : pkgName + "." + name));
    }
    return result.toArray(PsiPackage.EMPTY_ARRAY);
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass psiClass = findClass(qualifiedName, scope);
    return psiClass == null ? PsiClass.EMPTY_ARRAY : new PsiClass[]{psiClass};
  }

  @NotNull
  public static GlobalSearchScope addNonClasspathScope(@NotNull Project project, @NotNull GlobalSearchScope base) {
    List<GlobalSearchScope> nonClasspathScopes = new SmartList<>();
    for (PsiElementFinder finder : EP.getExtensions(project)) {
      if (finder instanceof NonClasspathClassFinder) {
        GlobalSearchScope scope = NonClasspathDirectoriesScope.compose(((NonClasspathClassFinder)finder).getClassRoots());
        if (scope != GlobalSearchScope.EMPTY_SCOPE) {
          nonClasspathScopes.add(scope);
        }
      }
    }
    if (nonClasspathScopes.isEmpty()) {
      return base;
    }
    return GlobalSearchScope.union(ArrayUtil.prepend(base, nonClasspathScopes.toArray(GlobalSearchScope.EMPTY_ARRAY)));
  }

  public PsiManager getPsiManager() {
    return myManager;
  }

  @Nullable
  private static VirtualFile findChild(@NotNull VirtualFile root,
                                       @NotNull String relPath,
                                       String @NotNull [] extensions) {
    VirtualFile file = null;
    for (String extension : extensions) {
      file = root.findChild(relPath + '.' + extension);
      if (file != null) break;
    }
    return file;
  }
}

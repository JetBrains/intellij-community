/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author peter
 */
public abstract class NonClasspathClassFinder extends PsiElementFinder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.NonClasspathClassFinder");
  private final AtomicLong myLastStamp = new AtomicLong();
  protected final Project myProject;
  private volatile List<VirtualFile> myCache;
  private final PsiManager myManager;
  private final boolean myCheckForSources;
  private final boolean myUseExtendedScope;

  public NonClasspathClassFinder(Project project) {
    this(project, false, false);
  }

  protected NonClasspathClassFinder(Project project, boolean checkForSources, boolean useExtendedScope) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    myUseExtendedScope = useExtendedScope;
    myCheckForSources = checkForSources;
  }

  protected List<VirtualFile> getClassRoots() {
    List<VirtualFile> cache = myCache;
    long stamp = myManager.getModificationTracker().getModificationCount();
    if (myLastStamp.get() != stamp) {
      cache = null;
    }

    if (cache != null && !cache.isEmpty()) {
      for (VirtualFile file : cache) {
        if (!file.isValid()) {
          cache = null;
          break;
        }
      }
    }

    if (cache == null) {
      myCache = cache = calcClassRoots();
      myLastStamp.set(stamp);
    }
    return cache;
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    if(myUseExtendedScope) {
      scope = addNonClasspathScope(myProject, scope);
    }
    final String relPath = qualifiedName.replace('.', '/');
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        if (myCheckForSources) {
          final VirtualFile classSrcFile = classRoot.findFileByRelativePath(relPath + JavaFileType.DOT_DEFAULT_EXTENSION);
          if (classSrcFile != null && classSrcFile.isValid()) {
            final PsiFile file = myManager.findFile(classSrcFile);
            if (file instanceof PsiJavaFile) {
              for (PsiClass aClass : ((PsiJavaFile)file).getClasses()) {
                if (qualifiedName.equals(aClass.getQualifiedName())) {
                  return aClass;
                }
              }
            }
          }
        }

        final VirtualFile classFile = classRoot.findFileByRelativePath(relPath + ".class");
        if (classFile != null) {
          if (!classFile.isValid()) {
            LOG.error("Invalid child of valid parent: " + classFile.getPath() + "; " + classRoot.isValid() + " path=" + classRoot.getPath());
            return null;
          }
          final PsiFile file = myManager.findFile(classFile);
          if (file instanceof PsiClassOwner) {
            final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
            if (classes.length == 1) {
              return classes[0];
            }
          }
        }
      }
    }
    return null;
  }

  protected abstract List<VirtualFile> calcClassRoots();

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return PsiClass.EMPTY_ARRAY;
    }

    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getQualifiedName();
        final VirtualFile dir = classRoot.findFileByRelativePath(pkgName.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (!file.isDirectory()) {
              final PsiFile psi = myManager.findFile(file);
              if (psi instanceof PsiClassOwner) {
                ContainerUtil.addAll(result, ((PsiClassOwner)psi).getClasses());
              }
            }
          }
        }
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }


  @NotNull
  @Override
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> result = new HashSet<String>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getQualifiedName();
        final VirtualFile dir = classRoot.findFileByRelativePath(pkgName.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (!file.isDirectory() && "class".equals(file.getExtension())) {
              result.add(file.getNameWithoutExtension());
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    for (final VirtualFile classRoot : classRoots) {
      final VirtualFile dir = classRoot.findFileByRelativePath(qualifiedName.replace('.', '/'));
      if (dir != null && dir.isDirectory()) {
        return createPackage(qualifiedName);
      }
    }
    return null;
  }

  private PsiPackageImpl createPackage(String qualifiedName) {
    return new PsiPackageImpl((PsiManagerEx)myManager, qualifiedName);
  }

  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return true;
    }

    final String qname = psiPackage.getQualifiedName();
    final PsiManager psiManager = psiPackage.getManager();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final VirtualFile dir = classRoot.findFileByRelativePath(qname.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          final PsiDirectory psiDirectory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
            @Override
            @Nullable
            public PsiDirectory compute() {
              return dir.isValid() ? psiManager.findDirectory(dir) : null;
            }
          });
          if (psiDirectory != null && !consumer.process(psiDirectory)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return super.getSubPackages(psiPackage, scope);
    }

    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getQualifiedName();
        final VirtualFile dir = classRoot.findFileByRelativePath(pkgName.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
              result.add(createPackage(pkgName + "." + file.getName()));
            }
          }
        }
      }
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
  public static GlobalSearchScope addNonClasspathScope(Project project, GlobalSearchScope base) {
    GlobalSearchScope scope = base;
    for (PsiElementFinder finder : Extensions.getExtensions(EP_NAME, project)) {
      if (finder instanceof NonClasspathClassFinder) {
        scope = scope.uniteWith(NonClasspathDirectoryScope.compose(((NonClasspathClassFinder)finder).getClassRoots()));
      }
    }
    return scope;
  }

  public PsiManager getPsiManager() {
    return myManager;
  }
}

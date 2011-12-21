/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CoreJavaFileManager extends PackageIndex implements JavaFileManager {
  private final CoreLocalFileSystem myLocalFileSystem;
  private final CoreJarFileSystem myJarFileSystem;
  private final List<File> myClasspath = new ArrayList<File>();
  private final PsiManager myPsiManager;

  public CoreJavaFileManager(PsiManager psiManager, CoreLocalFileSystem localFileSystem, CoreJarFileSystem jarFileSystem) {
    myPsiManager = psiManager;
    myLocalFileSystem = localFileSystem;
    myJarFileSystem = jarFileSystem;
  }

  @Override
  public PsiPackage findPackage(@NotNull String packageName) {
    final List<VirtualFile> files = findDirectoriesByPackageName(packageName);
    if (files.size() > 0) {
      return new PsiPackageImpl(myPsiManager, packageName);
    }
    return null;
  }

  private List<VirtualFile> findDirectoriesByPackageName(String packageName) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    String dirName = packageName.replace(".", "/");
    for (File file : myClasspath) {
      VirtualFile classDir = findUnderClasspathEntry(file, dirName);
      if (classDir != null) {
        result.add(classDir);
      }
    }
    return result;
  }

  @Nullable
  public PsiPackage getPackage(PsiDirectory dir) {
    final File ioFile = new File(dir.getVirtualFile().getPath());
    for (File root : myClasspath) {
      if (FileUtil.isAncestor(root, ioFile, false)) {
        final String relativePath = FileUtil.getRelativePath(root.getPath(), ioFile.getPath(), '.');
        return new PsiPackageImpl(myPsiManager, relativePath);
      }
    }
    return null;
  }
  
  @Nullable
  private VirtualFile findUnderClasspathEntry(File classpathEntry, String relativeName) {
    if (classpathEntry.isFile()) {
       return myJarFileSystem.findFileByPath(classpathEntry.getPath() + "!/" + relativeName);
    }
    else {
      return myLocalFileSystem.findFileByPath(new File(classpathEntry, relativeName).getPath());
    }
  }

  @Override
  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return new CollectionQuery<VirtualFile>(findDirectoriesByPackageName(packageName));
  }

  @Override
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    for (File file : myClasspath) {
      final PsiClass psiClass = findClassInClasspathEntry(qName, file);
      if (psiClass != null) {
        return psiClass;
      }
    }
    return null;
  }

  @Nullable
  private PsiClass findClassInClasspathEntry(String qName, File file) {
    String fileName = qName.replace(".", "/") + ".class";
    VirtualFile classFile = findUnderClasspathEntry(file, fileName);
    
    if (classFile != null) {
      PsiFile psiFile = myPsiManager.findFile(classFile);
      if (!(psiFile instanceof PsiJavaFile)) {
        throw new UnsupportedOperationException("no java file for .class");
      }
      final PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
      if (classes.length == 1) {
        return classes[0];
      }
    }
    return null;
  }

  @Override
  public PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (File file : myClasspath) {
      final PsiClass psiClass = findClassInClasspathEntry(qName, file);
      if (psiClass != null) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @Override
  public Collection<String> getNonTrivialPackagePrefixes() {
    return Collections.emptyList();
  }

  @Override
  public void initialize() {
  }

  public void addToClasspath(File path) {
    myClasspath.add(path);
  }
}

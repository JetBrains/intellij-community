/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class CoreJavaFileManager implements JavaFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.CoreJavaFileManager");

  private final List<VirtualFile> myClasspath = new ArrayList<VirtualFile>();

  private final PsiManager myPsiManager;

  public CoreJavaFileManager(PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  private List<VirtualFile> roots() {
    return myClasspath;
  }

  @Override
  public PsiPackage findPackage(@NotNull String packageName) {
    final List<VirtualFile> files = findDirectoriesByPackageName(packageName);
    if (!files.isEmpty()) {
      return new PsiPackageImpl(myPsiManager, packageName);
    }
    return null;
  }

  private List<VirtualFile> findDirectoriesByPackageName(String packageName) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    String dirName = packageName.replace(".", "/");
    for (VirtualFile root : roots()) {
      VirtualFile classDir = root.findFileByRelativePath(dirName);
      if (classDir != null) {
        result.add(classDir);
      }
    }
    return result;
  }

  @Nullable
  public PsiPackage getPackage(PsiDirectory dir) {
    final VirtualFile file = dir.getVirtualFile();
    for (VirtualFile root : myClasspath) {
      if (VfsUtilCore.isAncestor(root, file, false)) {
        String relativePath = FileUtil.getRelativePath(root.getPath(), file.getPath(), '/');
        if (relativePath == null) continue;
        return new PsiPackageImpl(myPsiManager, relativePath.replace('/', '.'));
      }
    }
    return null;
  }

  @Override
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    for (VirtualFile root : roots()) {
      final PsiClass psiClass = findClassInClasspathRoot(qName, root, myPsiManager, scope);
      if (psiClass != null) {
        return psiClass;
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass findClassInClasspathRoot(@NotNull String qName,
                                                  @NotNull VirtualFile root,
                                                  @NotNull PsiManager psiManager,
                                                  @NotNull GlobalSearchScope scope) {
    String pathRest = qName;
    VirtualFile cur = root;

    while (true) {
      int dot = pathRest.indexOf('.');
      if (dot < 0) break;

      String pathComponent = pathRest.substring(0, dot);
      VirtualFile child = cur.findChild(pathComponent);

      if (child == null) break;
      pathRest = pathRest.substring(dot + 1);
      cur = child;
    }

    String classNameWithInnerClasses = pathRest;
    String topLevelClassName = substringBeforeFirstDot(classNameWithInnerClasses);

    VirtualFile vFile = cur.findChild(topLevelClassName + ".class");
    if (vFile == null) vFile = cur.findChild(topLevelClassName + ".java");

    if (vFile == null) {
      return null;
    }
    if (!vFile.isValid()) {
      LOG.error("Invalid child of valid parent: " + vFile.getPath() + "; " + root.isValid() + " path=" + root.getPath());
      return null;
    }
    if (!scope.contains(vFile)) {
      return null;
    }

    final PsiFile file = psiManager.findFile(vFile);
    if (!(file instanceof PsiClassOwner)) {
      return null;
    }

    return findClassInPsiFile(classNameWithInnerClasses, (PsiClassOwner)file);
  }

  @NotNull
  private static String substringBeforeFirstDot(@NotNull String classNameWithInnerClasses) {
    int dot = classNameWithInnerClasses.indexOf('.');
    if (dot < 0) {
      return classNameWithInnerClasses;
    }
    else {
      return classNameWithInnerClasses.substring(0, dot);
    }
  }

  @Nullable
  private static PsiClass findClassInPsiFile(@NotNull String classNameWithInnerClassesDotSeparated, @NotNull PsiClassOwner file) {
    for (PsiClass topLevelClass : file.getClasses()) {
      PsiClass candidate = findClassByTopLevelClass(classNameWithInnerClassesDotSeparated, topLevelClass);
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass findClassByTopLevelClass(@NotNull String className, @NotNull PsiClass topLevelClass) {
    if (className.indexOf('.') < 0) {
      return className.equals(topLevelClass.getName()) ? topLevelClass : null;
    }

    Iterator<String> segments = StringUtil.split(className, ".").iterator();
    if (!segments.hasNext() || !segments.next().equals(topLevelClass.getName())) {
      return null;
    }
    PsiClass curClass = topLevelClass;
    while (segments.hasNext()) {
      String innerClassName = segments.next();
      PsiClass innerClass = curClass.findInnerClassByName(innerClassName, false);
      if (innerClass == null) {
        return null;
      }
      curClass = innerClass;
    }
    return curClass;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (VirtualFile file : roots()) {
      final PsiClass psiClass = findClassInClasspathRoot(qName, file, myPsiManager, scope);
      if (psiClass != null) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  @Override
  public Collection<String> getNonTrivialPackagePrefixes() {
    return Collections.emptyList();
  }

  public void addToClasspath(VirtualFile root) {
    myClasspath.add(root);
  }
}

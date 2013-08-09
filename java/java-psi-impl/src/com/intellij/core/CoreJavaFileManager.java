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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
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
    if (files.size() > 0) {
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
      final PsiClass psiClass = findClassInClasspathRoot(qName, root, myPsiManager);
      if (psiClass != null) {
        return psiClass;
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass findClassInClasspathRoot(String qName, VirtualFile root, PsiManager psiManager) {
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

    String className = pathRest.replace('.', '$');
    int bucks = className.indexOf('$');

    String rootClassName;
    if (bucks < 0) {
      rootClassName = className;
    }
    else {
      rootClassName = className.substring(0, bucks);
      className = className.substring(bucks + 1);
    }

    VirtualFile vFile = cur.findChild(rootClassName + ".class");
    if (vFile == null) vFile = cur.findChild(rootClassName + ".java");

    if (vFile != null) {
      if (!vFile.isValid()) {
        LOG.error("Invalid child of valid parent: " + vFile.getPath() + "; " + root.isValid() + " path=" + root.getPath());
        return null;
      }

      final PsiFile file = psiManager.findFile(vFile);
      if (file instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
        if (classes.length == 1) {
          PsiClass curClass = classes[0];

            if (bucks > 0) {
              Stack<ClassAndOffsets> currentPath = new Stack<ClassAndOffsets>();
              currentPath.add(new ClassAndOffsets(curClass, 0, 0));
              currentPath.add(currentPath.peek());

              while (currentPath.size() > 1) {
                ClassAndOffsets classAndOffset = currentPath.pop();
                int newComponentStart = classAndOffset.componentStart;
                int lookupStart = classAndOffset.lookupStart;
                curClass = currentPath.peek().clazz; //owner class

                while (lookupStart <= className.length()) {
                  int bucksIndex = className.indexOf("$", lookupStart);
                  bucksIndex =  bucksIndex < 0 ? className.length(): bucksIndex;

                  String component = className.substring(newComponentStart, bucksIndex);
                  PsiClass inner = curClass.findInnerClassByName(component, false);

                  lookupStart = bucksIndex + 1;
                  if (inner == null) {
                    continue;
                  }

                  currentPath.add(new ClassAndOffsets(inner, newComponentStart, lookupStart));

                  newComponentStart = lookupStart;
                  curClass = inner;
                }

                if (lookupStart == newComponentStart) {
                  return curClass;
                }
              }

              return null;

            } else {
              return curClass;
            }
          }
        }
      }

    return null;
  }

  @Override
  public PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (VirtualFile file : roots()) {
      final PsiClass psiClass = findClassInClasspathRoot(qName, file, myPsiManager);
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

  public void addToClasspath(VirtualFile root) {
    myClasspath.add(root);
  }

  private static class ClassAndOffsets {

    final PsiClass clazz;
    final int componentStart;
    final int lookupStart;

    ClassAndOffsets(PsiClass clazz, int componentStart, int lookupStart) {
      this.clazz = clazz;
      this.componentStart = componentStart;
      this.lookupStart = lookupStart;
    }
  }
}

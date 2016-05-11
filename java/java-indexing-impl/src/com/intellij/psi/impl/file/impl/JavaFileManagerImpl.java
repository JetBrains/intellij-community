/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.file.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;

/**
 * Author: dmitry lomov
 */
public class JavaFileManagerImpl implements JavaFileManager, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.JavaFileManagerImpl");
  private final PsiManagerEx myManager;
  private volatile Set<String> myNontrivialPackagePrefixes;
  private boolean myDisposed;
  private final PackageIndex myPackageIndex;

  public JavaFileManagerImpl(Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
    myPackageIndex = PackageIndex.getInstance(myManager.getProject());
    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(final ModuleRootEvent event) {
        myNontrivialPackagePrefixes = null;
      }
    });
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @Override
  @Nullable
  public PsiPackage findPackage(@NotNull String packageName) {
    Query<VirtualFile> dirs = myPackageIndex.getDirsByPackageName(packageName, true);
    if (dirs.findFirst() == null) return null;
    return new PsiPackageImpl(myManager, packageName);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qName, @NotNull final GlobalSearchScope scope) {
    List<Pair<PsiClass, VirtualFile>> result = doFindClasses(qName, scope);

    int count = result.size();
    if (count == 0) return PsiClass.EMPTY_ARRAY;
    if (count == 1) return new PsiClass[] {result.get(0).getFirst()};

    ContainerUtil.quickSort(result, (o1, o2) -> scope.compare(o2.getSecond(), o1.getSecond()));

    return result.stream().map(p -> p.getFirst()).toArray(PsiClass[]::new);
  }

  @NotNull
  private List<Pair<PsiClass, VirtualFile>> doFindClasses(@NotNull String qName, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiClass> classes = JavaFullClassNameIndex.getInstance().get(qName.hashCode(), myManager.getProject(), scope);
    if (classes.isEmpty()) return Collections.emptyList();
    List<Pair<PsiClass, VirtualFile>> result = new ArrayList<>(classes.size());
    for (PsiClass aClass : classes) {
      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals(qName)) continue;

      PsiFile file = aClass.getContainingFile();
      if (file == null) {
        throw new AssertionError("No file for class: " + aClass + " of " + aClass.getClass());
      }
      final boolean valid = file.isValid();
      VirtualFile vFile = file.getVirtualFile();
      if (!valid) {
        LOG.error("Invalid file " +
                  file + "; virtualFile:" + vFile +
                  (vFile != null && !vFile.isValid() ? " (invalid)" : "") +
                  "; id=" + (vFile == null ? 0 : ((VirtualFileWithId)vFile).getId()),
                  new PsiInvalidElementAccessException(aClass));
        continue;
      }
      if (!hasAcceptablePackage(vFile)) continue;

      result.add(Pair.create(aClass, vFile));
    }

    return result;
  }

  @Override
  @Nullable
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
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
    if (vFile.getFileType() == JavaClassFileType.INSTANCE) {
      // See IDEADEV-5626
      final VirtualFile root = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getClassRootForFile(vFile);
      VirtualFile parent = vFile.getParent();
      final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myManager.getProject());
      while (parent != null && !Comparing.equal(parent, root)) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  @NotNull
  @Override
  public Collection<String> getNonTrivialPackagePrefixes() {
    Set<String> names = myNontrivialPackagePrefixes;
    if (names == null) {
      names = new HashSet<>();
      final ProjectRootManager rootManager = ProjectRootManager.getInstance(myManager.getProject());
      final List<VirtualFile> sourceRoots = rootManager.getModuleSourceRoots(JavaModuleSourceRootTypes.SOURCES);
      final ProjectFileIndex fileIndex = rootManager.getFileIndex();
      for (final VirtualFile sourceRoot : sourceRoots) {
        if (sourceRoot.isDirectory()) {
          final String packageName = fileIndex.getPackageNameByDirectory(sourceRoot);
          if (packageName != null && !packageName.isEmpty()) {
            names.add(packageName);
          }
        }
      }
      myNontrivialPackagePrefixes = names;
    }
    return names;
  }

}

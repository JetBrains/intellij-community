// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdIterator;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class MetaAnnotationUtilImpl extends MetaAnnotationUtil {
  @Override
  protected GlobalSearchScope getAllAnnotationFilesScope(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope javaScope = new FileIdScope(project, getJavaAnnotationInheritorIds(project));
      GlobalSearchScope otherScope = searchForAnnotationInheritorsInOtherLanguages(project);
      return CachedValueProvider.Result.createSingleDependency(
        javaScope.uniteWith(otherScope),
        PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @NotNull
  private static GlobalSearchScope searchForAnnotationInheritorsInOtherLanguages(Project project) {
    Set<VirtualFile> allAnnotationFiles = new HashSet<>();
    for (PsiClass javaLangAnnotation : JavaPsiFacade.getInstance(project)
      .findClasses(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, GlobalSearchScope.allScope(project))) {
      DirectClassInheritorsSearch.search(javaLangAnnotation, new NonJavaScope(project), false).forEach(annotationClass -> {
        ProgressManager.checkCanceled();
        ContainerUtil.addIfNotNull(allAnnotationFiles, PsiUtilCore.getVirtualFile(annotationClass));
        return true;
      });
    }

    return GlobalSearchScope.filesWithLibrariesScope(project, allAnnotationFiles);
  }

  @NotNull
  private static TIntHashSet getJavaAnnotationInheritorIds(Project project) {
    IdIterator iterator = StubIndex.getInstance().getContainingIds(JavaStubIndexKeys.SUPER_CLASSES, "Annotation", project,
                                                                   GlobalSearchScope.allScope(project));
    TIntHashSet idSet = new TIntHashSet();
    while (iterator.hasNext()) {
      idSet.add(iterator.next());
    }
    return idSet;
  }

  private static class FileIdScope extends GlobalSearchScope {
    private final TIntHashSet myIdSet;

    FileIdScope(Project project, TIntHashSet idSet) {
      super(project);
      myIdSet = idSet;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return file instanceof VirtualFileWithId && myIdSet.contains(((VirtualFileWithId)file).getId());
    }
  }

  private static class NonJavaScope extends GlobalSearchScope {
    NonJavaScope(Project project) {
      super(project);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      if (FileTypeManager.getInstance().isFileOfType(file, StdFileTypes.JAVA)) {
        return false;
      }

      if (FileTypeManager.getInstance().isFileOfType(file, StdFileTypes.CLASS)) {
        return ClassFileDecompilers.find(file) instanceof ClassFileDecompilers.Full;
      }

      return true;
    }
  }
}

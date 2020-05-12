// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ExceptionInfoCache {
  private final ConcurrentMap<String, ClassResolveInfo> myCache = ContainerUtil.createConcurrentSoftValueMap();
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;

  public ExceptionInfoCache(GlobalSearchScope searchScope) {
    myProject = Objects.requireNonNull(searchScope.getProject());
    mySearchScope = searchScope;
  }

  @NotNull public Project getProject() {
    return myProject;
  }

  private PsiClass @NotNull [] findClassesPreferringMyScope(String className) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    PsiClass[] result = psiFacade.findClasses(className, mySearchScope);
    return result.length != 0 ? result : psiFacade.findClasses(className, GlobalSearchScope.allScope(myProject));
  }

  ClassResolveInfo resolveClass(String className) {
    ClassResolveInfo cached = myCache.get(className);
    if (cached != null) {
      return cached;
    }

    if (DumbService.isDumb(myProject)) {
      return ClassResolveInfo.EMPTY;
    }

    PsiClass[] classes = findClassesPreferringMyScope(className);
    if (classes.length == 0) {
      final int dollarIndex = className.indexOf('$');
      if (dollarIndex >= 0) {
        classes = findClassesPreferringMyScope(className.substring(0, dollarIndex));
      }
    }

    ClassResolveInfo result = ClassResolveInfo.create(myProject, classes);
    myCache.put(className, result);
    return result;
  }

  static class ClassResolveInfo {
    static final ClassResolveInfo EMPTY = new ClassResolveInfo(Collections.emptyMap(), false);
    
    final Map<VirtualFile, PsiElement> myClasses;
    final boolean myInLibrary;

    ClassResolveInfo(Map<VirtualFile, PsiElement> classes, boolean library) {
      myClasses = classes;
      myInLibrary = library;
    }

    @NotNull
    static ExceptionInfoCache.ClassResolveInfo create(Project project, PsiElement[] elements) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      Map<VirtualFile, PsiElement> result = new LinkedHashMap<>();
      boolean library = true;
      for (PsiElement element : elements) {
        element = element.getNavigationElement();
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file.getVirtualFile();
        if (index.isInContent(virtualFile)) {
          if (library) {
            library = false;
            result.clear();
          }
        }
        else if (!library) {
          continue;
        }
        result.put(virtualFile, element);
      }
      return new ClassResolveInfo(result, library);
    }
  }

}

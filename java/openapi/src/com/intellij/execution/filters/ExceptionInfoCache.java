// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ExceptionInfoCache {
  private final ConcurrentMap<String, Pair<PsiClass[], PsiFile[]>> myCache = ContainerUtil.createConcurrentSoftValueMap();
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

  Pair<PsiClass[], PsiFile[]> resolveClass(String className) {
    Pair<PsiClass[], PsiFile[]> cached = myCache.get(className);
    if (cached != null) {
      return cached;
    }

    if (DumbService.isDumb(myProject)) {
      return Pair.create(PsiClass.EMPTY_ARRAY, PsiFile.EMPTY_ARRAY);
    }

    PsiClass[] classes = findClassesPreferringMyScope(className);
    if (classes.length == 0) {
      final int dollarIndex = className.indexOf('$');
      if (dollarIndex >= 0) {
        classes = findClassesPreferringMyScope(className.substring(0, dollarIndex));
      }
    }

    PsiFile[] files = new PsiFile[classes.length];
    for (int i = 0; i < classes.length; i++) {
      files[i] = (PsiFile)classes[i].getContainingFile().getNavigationElement();
    }

    Pair<PsiClass[], PsiFile[]> result = Pair.create(classes, files);
    myCache.put(className, result);
    return result;
  }

}

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
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ExceptionInfoCache {
  private final ConcurrentMap<String, Pair<PsiClass[], PsiFile[]>> myCache = ContainerUtil.createConcurrentSoftValueMap();
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;

  public ExceptionInfoCache(GlobalSearchScope searchScope) {
    myProject = ObjectUtils.assertNotNull(searchScope.getProject());
    mySearchScope = searchScope;
  }

  @NotNull public Project getProject() {
    return myProject;
  }

  @NotNull
  private PsiClass[] findClassesPreferringMyScope(String className) {
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

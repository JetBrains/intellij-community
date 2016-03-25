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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters parameters, @NotNull final Processor<PsiClass> consumer) {
    final PsiClass aClass = parameters.getClassToProcess();

    final SearchScope useScope = ApplicationManager.getApplication().runReadAction((Computable<SearchScope>)aClass::getUseScope);

    final Project project = PsiUtilCore.getProjectInReadAction(aClass);
    if (JavaClassInheritorsSearcher.isJavaLangObject(aClass)) {
      return AllClassesSearch.search(useScope, project).forEach(psiClass -> {
        ProgressManager.checkCanceled();
        if (psiClass.isInterface()) {
          return consumer.process(psiClass);
        }
        final PsiClass superClass = psiClass.getSuperClass();
        return superClass == null || !JavaClassInheritorsSearcher.isJavaLangObject(superClass) || consumer.process(psiClass);
      });
    }

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(project);
    final String searchKey = ApplicationManager.getApplication().runReadAction((Computable<String>)aClass::getName);
    if (StringUtil.isEmpty(searchKey)) {
      return true;
    }

    Collection<PsiReferenceList> candidates =
      MethodUsagesSearcher.resolveInReadAction(project, () -> JavaSuperClassNameOccurenceIndex.getInstance().get(searchKey, project, scope));

    Map<String, List<PsiClass>> classes = new HashMap<>();

    for (final PsiReferenceList referenceList : candidates) {
      ProgressManager.checkCanceled();
      final PsiClass candidate = (PsiClass)ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)referenceList::getParent);
      if (!checkInheritance(parameters, aClass, candidate, project)) continue;

      String fqn = ApplicationManager.getApplication().runReadAction((Computable<String>)candidate::getQualifiedName);
      List<PsiClass> list = classes.get(fqn);
      if (list == null) {
        list = new ArrayList<>();
        classes.put(fqn, list);
      }
      list.add(candidate);
    }

    if (!classes.isEmpty()) {
      final VirtualFile jarFile = getJarFile(aClass);
      for (List<PsiClass> sameNamedClasses : classes.values()) {
        ProgressManager.checkCanceled();
        if (!processSameNamedClasses(sameNamedClasses, jarFile, consumer)) return false;
      }
    }

    if (parameters.includeAnonymous()) {
      Collection<PsiAnonymousClass> anonymousCandidates =
        MethodUsagesSearcher.resolveInReadAction(project, () -> JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(searchKey, project, scope));

      for (PsiAnonymousClass candidate : anonymousCandidates) {
        ProgressManager.checkCanceled();
        if (!checkInheritance(parameters, aClass, candidate, project)) continue;

        if (!consumer.process(candidate)) return false;
      }

      boolean isEnum = ApplicationManager.getApplication().runReadAction((Computable<Boolean>)aClass::isEnum);
      if (isEnum) {
        // abstract enum can be subclassed in the body
        PsiField[] fields = ApplicationManager.getApplication().runReadAction((Computable<PsiField[]>)aClass::getFields);
        for (final PsiField field : fields) {
          ProgressManager.checkCanceled();
          if (field instanceof PsiEnumConstant) {
            PsiEnumConstantInitializer initializingClass =
              ApplicationManager.getApplication().runReadAction((Computable<PsiEnumConstantInitializer>)((PsiEnumConstant)field)::getInitializingClass);
            if (initializingClass != null) {
              if (!consumer.process(initializingClass)) return false;
            }
          }
        }
      }
    }

    return true;
  }

  private static boolean checkInheritance(@NotNull DirectClassInheritorsSearch.SearchParameters p,
                                          @NotNull PsiClass aClass,
                                          @NotNull PsiClass candidate,
                                          @NotNull Project project) {
    return MethodUsagesSearcher.resolveInReadAction(project, () -> !p.isCheckInheritance() || candidate.isInheritor(aClass, false));
  }

  private static boolean processSameNamedClasses(@NotNull List<PsiClass> sameNamedClasses,
                                                 @Nullable VirtualFile jarFile,
                                                 @NotNull Processor<PsiClass> consumer) {
    // if there is a class from the same jar, prefer it
    boolean sameJarClassFound = false;

    if (jarFile != null && sameNamedClasses.size() > 1) {
      for (PsiClass sameNamedClass : sameNamedClasses) {
        ProgressManager.checkCanceled();
        boolean fromSameJar = Comparing.equal(getJarFile(sameNamedClass), jarFile);
        if (fromSameJar) {
          sameJarClassFound = true;
          if (!consumer.process(sameNamedClass)) return false;
        }
      }
    }

    return sameJarClassFound || ContainerUtil.process(sameNamedClasses, consumer);
  }

  private static VirtualFile getJarFile(@NotNull PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> PsiUtil.getJarFile(aClass));
  }
}

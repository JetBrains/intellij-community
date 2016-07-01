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

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters parameters, @NotNull final Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    assert parameters.isCheckInheritance();

    final SearchScope useScope = ApplicationManager.getApplication().runReadAction((Computable<SearchScope>)baseClass::getUseScope);

    final Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (JavaClassInheritorsSearcher.isJavaLangObject(baseClass)) {
      return AllClassesSearch.search(useScope, project).forEach(psiClass -> {
        ProgressManager.checkCanceled();
        if (psiClass.isInterface()) {
          return consumer.process(psiClass);
        }
        final PsiClass superClass = psiClass.getSuperClass();
        return superClass == null || !JavaClassInheritorsSearcher.isJavaLangObject(superClass) || consumer.process(psiClass);
      });
    }

    SearchScope scope = parameters.getScope();
    PsiClass[] cache = getOrCalculateDirectSubClasses(project, baseClass, useScope);

    if (cache.length == 0) {
      return true;
    }

    VirtualFile baseClassJarFile = null;
    // iterate by same-FQN groups. For each group process only same-jar subclasses, or all of them if they are all outside the jarFile.
    int groupStart = 0;
    boolean sameJarClassFound = false;
    String currentFQN = null;
    boolean[] isOutOfScope = new boolean[cache.length]; // here we cache results of isInScope(scope, subClass) to avoid calculating it twice
    for (int i = 0; i <= cache.length; i++) {
      ProgressManager.checkCanceled();

      PsiClass subClass = i == cache.length ? null : cache[i];
      if (subClass instanceof PsiAnonymousClass) {
        // we reached anonymous classes tail, process them all and exit
        if (!parameters.includeAnonymous()) {
          return true;
        }
      }
      if (i != cache.length && !isInScope(scope, subClass)) {
        isOutOfScope[i] = true;
        continue;
      }

      String fqn = i == cache.length ? null : ApplicationManager.getApplication().runReadAction((Computable<String>)subClass::getQualifiedName);

      if (currentFQN != null && Comparing.equal(fqn, currentFQN)) {
        VirtualFile currentJarFile = getJarFile(subClass);
        if (baseClassJarFile == null) {
          baseClassJarFile = getJarFile(baseClass);
        }
        boolean fromSameJar = Comparing.equal(currentJarFile, baseClassJarFile);
        if (fromSameJar) {
          if (!consumer.process(subClass)) return false;
          sameJarClassFound = true;
        }
      }
      else {
        currentFQN = fqn;
        // the end of the same-FQN group. Process only same-jar classes in subClasses[groupStart..i-1] group or the whole group if there were none.
        if (!sameJarClassFound) {
          for (int g=groupStart; g<i; g++) {
            ProgressManager.checkCanceled();
            if (isOutOfScope[g]) continue;
            PsiClass subClassCandidate = cache[g];
            if (!consumer.process(subClassCandidate)) return false;
          }
        }
        groupStart = i;
        sameJarClassFound = false;
      }
    }

    return true;
  }

  private static boolean isInScope(@NotNull SearchScope scope, @NotNull PsiClass subClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> PsiSearchScopeUtil.isInScope(scope, subClass));
  }

  // The list starts with non-anonymous classes, ends with anonymous sub classes
  // Classes grouped by their FQN. (Because among the same-named subclasses we should return only the same-jar ones, or all of them if there were none)
  @NotNull
  private static PsiClass[] getOrCalculateDirectSubClasses(@NotNull Project project, @NotNull PsiClass baseClass, @NotNull SearchScope useScope) {
    ConcurrentMap<PsiClass, PsiClass[]> map = HighlightingCaches.getInstance(project).DIRECT_SUB_CLASSES;
    PsiClass[] cache = map.get(baseClass);
    if (cache != null) {
      return cache;
    }

    final String baseClassName = ApplicationManager.getApplication().runReadAction((Computable<String>)baseClass::getName);
    if (StringUtil.isEmpty(baseClassName)) {
      return PsiClass.EMPTY_ARRAY;
    }
    cache = calculateDirectSubClasses(project, baseClass, baseClassName, useScope);
    // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
    if (ApplicationManager.getApplication().runReadAction((Computable<Boolean>)baseClass::isPhysical)) {
      cache = ConcurrencyUtil.cacheOrGet(map, baseClass, cache);
    }
    return cache;
  }

  private static <T> boolean processConcurrentlyIfTooMany(@NotNull Collection<T> collection, @NotNull Processor<? super T> processor) {
    int size = collection.size();
    if (size == 0) {
      return true;
    }
    if (size > 100) {
      return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(collection), ProgressIndicatorProvider.getGlobalProgressIndicator(), true, processor);
    }
    return ContainerUtil.process(collection, processor);
  }

  @NotNull
  private static PsiClass[] calculateDirectSubClasses(@NotNull Project project,
                                                      @NotNull PsiClass baseClass,
                                                      @NotNull String baseClassName,
                                                      @NotNull SearchScope useScope) {
    DumbService dumbService = DumbService.getInstance(project);
    GlobalSearchScope globalUseScope = dumbService.runReadActionInSmartMode(
      () -> StubHierarchyInheritorSearcher.restrictScope(GlobalSearchScopeUtil.toGlobalSearchScope(useScope, project)));
    Collection<PsiReferenceList> candidates =
      dumbService.runReadActionInSmartMode(() -> JavaSuperClassNameOccurenceIndex.getInstance().get(baseClassName, project, globalUseScope));

    // memory/speed optimisation: it really is a map(string -> PsiClass or List<PsiClass>)
    final Map<String, Object> classesWithFqn = new HashMap<>();

    processConcurrentlyIfTooMany(candidates,
       referenceList -> {
         ProgressManager.checkCanceled();
         ApplicationManager.getApplication().runReadAction(() -> {
           final PsiClass candidate = (PsiClass)referenceList.getParent();
           boolean isInheritor = candidate.isInheritor(baseClass, false);
           if (isInheritor) {
             String fqn = candidate.getQualifiedName();
             synchronized (classesWithFqn) {
               Object value = classesWithFqn.get(fqn);
               if (value == null) {
                 classesWithFqn.put(fqn, candidate);
               }
               else if (value instanceof PsiClass) {
                 List<PsiClass> list = new ArrayList<>();
                 list.add((PsiClass)value);
                 list.add(candidate);
                 classesWithFqn.put(fqn, list);
               }
               else {
                 @SuppressWarnings("unchecked")
                 List<PsiClass> list = (List<PsiClass>)value;
                 list.add(candidate);
               }
             }
           }
         });

         return true;
       });

    final List<PsiClass> result = new ArrayList<>();
    for (Object value : classesWithFqn.values()) {
      if (value instanceof PsiClass) {
        result.add((PsiClass)value);
      }
      else {
        @SuppressWarnings("unchecked")
        List<PsiClass> list = (List<PsiClass>)value;
        result.addAll(list);
      }
    }

    Collection<PsiAnonymousClass> anonymousCandidates =
      dumbService.runReadActionInSmartMode(() -> JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(baseClassName, project, globalUseScope));

    processConcurrentlyIfTooMany(anonymousCandidates,
       candidate-> {
         boolean isInheritor = dumbService.runReadActionInSmartMode(() -> candidate.isInheritor(baseClass, false));
         if (isInheritor) {
           synchronized (result) {
             result.add(candidate);
           }
         }
         return true;
       });

    boolean isEnum = ApplicationManager.getApplication().runReadAction((Computable<Boolean>)baseClass::isEnum);
    if (isEnum) {
      // abstract enum can be subclassed in the body
      PsiField[] fields = ApplicationManager.getApplication().runReadAction((Computable<PsiField[]>)baseClass::getFields);
      for (final PsiField field : fields) {
        ProgressManager.checkCanceled();
        if (field instanceof PsiEnumConstant) {
          PsiEnumConstantInitializer initializingClass =
            ApplicationManager.getApplication().runReadAction((Computable<PsiEnumConstantInitializer>)((PsiEnumConstant)field)::getInitializingClass);
          if (initializingClass != null) {
            result.add(initializingClass); // it surely is an inheritor
          }
        }
      }
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private static VirtualFile getJarFile(@NotNull PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> PsiUtil.getJarFile(aClass));
  }
}

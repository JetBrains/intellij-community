/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters parameters, @NotNull final Processor<PsiClass> consumer) {
    PsiClass baseClass = getClassToSearch(parameters);
    assert parameters.isCheckInheritance();

    final Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (JavaClassInheritorsSearcher.isJavaLangObject(baseClass)) {
      SearchScope useScope = ReadAction.compute(baseClass::getUseScope);
      return AllClassesSearch.search(useScope, project).forEach(psiClass -> {
        ProgressManager.checkCanceled();
        if (psiClass.isInterface()) {
          return consumer.process(psiClass);
        }
        final PsiClass superClass = psiClass.getSuperClass();
        return superClass == null || !JavaClassInheritorsSearcher.isJavaLangObject(superClass) || consumer.process(psiClass);
      });
    }

    SearchScope scope;
    SearchScope useScope;
    CompilerDirectHierarchyInfo info = performSearchUsingCompilerIndices(parameters, project);
    if (info == null) {
      scope = parameters.getScope();
      useScope = ReadAction.compute(baseClass::getUseScope);
    }
    else {
      if (!processInheritorCandidates(info.getHierarchyChildren(), consumer, parameters.includeAnonymous())) return false;
      scope = ReadAction.compute(() -> parameters.getScope().intersectWith(info.getDirtyScope()));
      useScope = ReadAction.compute(() -> baseClass.getUseScope().intersectWith(info.getDirtyScope()));
    }

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

      String fqn = i == cache.length ? null : ReadAction.compute(subClass::getQualifiedName);

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

  private static PsiClass getClassToSearch(@NotNull DirectClassInheritorsSearch.SearchParameters parameters) {
    return ReadAction.compute(() -> (PsiClass)PsiUtil.preferCompiledElement(parameters.getClassToProcess()));
  }

  private static boolean isInScope(@NotNull SearchScope scope, @NotNull PsiClass subClass) {
    return ReadAction.compute(() -> PsiSearchScopeUtil.isInScope(scope, subClass));
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

    final String baseClassName = ReadAction.compute(baseClass::getName);
    if (StringUtil.isEmpty(baseClassName)) {
      return PsiClass.EMPTY_ARRAY;
    }
    cache = calculateDirectSubClasses(project, baseClass, baseClassName, useScope);
    // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
    if (ReadAction.compute(baseClass::isPhysical)) {
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

    boolean isEnum = ReadAction.compute(baseClass::isEnum);
    if (isEnum) {
      // abstract enum can be subclassed in the body
      PsiField[] fields = ReadAction.compute(baseClass::getFields);
      for (final PsiField field : fields) {
        ProgressManager.checkCanceled();
        if (field instanceof PsiEnumConstant) {
          PsiEnumConstantInitializer initializingClass =
            ReadAction.compute(((PsiEnumConstant)field)::getInitializingClass);
          if (initializingClass != null) {
            result.add(initializingClass); // it surely is an inheritor
          }
        }
      }
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private static VirtualFile getJarFile(@NotNull PsiClass aClass) {
    return ReadAction.compute(() -> PsiUtil.getJarFile(aClass));
  }

  @Nullable
  private static CompilerDirectHierarchyInfo performSearchUsingCompilerIndices(@NotNull DirectClassInheritorsSearch.SearchParameters parameters,
                                                                               @NotNull Project project) {
    SearchScope scope = parameters.getScope();
    if (!(scope instanceof GlobalSearchScope)) return null;

    CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstance(project);
    if (compilerReferenceService == null) return null; //This is possible in CLion, where Java compiler is not defined

    return compilerReferenceService.getDirectInheritors(getClassToSearch(parameters),
                                                        (GlobalSearchScope)scope,
                                                        JavaFileType.INSTANCE);
  }

  private static boolean processInheritorCandidates(@NotNull Stream<PsiElement> classStream,
                                                    @NotNull Processor<PsiClass> consumer,
                                                    boolean acceptAnonymous) {
    if (!acceptAnonymous) {
      classStream = classStream.filter(c -> !(c instanceof PsiAnonymousClass));
    }
    return ContainerUtil.process(classStream.iterator(), e -> {
      ProgressManager.checkCanceled();
      PsiClass c = (PsiClass) e;
      return consumer.process(c);
    });
  }
}

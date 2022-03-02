// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters parameters, @NotNull final Processor<? super PsiClass> processor) {
    if (!parameters.shouldSearchInLanguage(JavaLanguage.INSTANCE)) {
      return true;
    }

    PsiClass baseClass = getClassToSearch(parameters);
    assert parameters.isCheckInheritance();

    SearchScope scope = parameters.getScope();

    final Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (JavaClassInheritorsSearcher.isJavaLangObject(baseClass)) {
      SearchScope useScope = ReadAction.compute(baseClass::getUseScope);
      SearchScope actualScope = useScope.intersectWith(scope);
      return AllClassesSearch.search(actualScope, project).allowParallelProcessing().forEach(psiClass -> {
        ProgressManager.checkCanceled();
        if (shortCircuitCandidate(psiClass)) return true;
        return processor.process(psiClass);
      });
    }

    PsiClass[] cache = getOrCalculateDirectSubClasses(project, baseClass, parameters);

    if (cache.length == 0) {
      return true;
    }

    VirtualFile baseClassJarFile = null;
    // iterate by same-FQN groups. For each group process only same-jar subclasses, or all of them if they are all outside the jarFile.
    int groupStart = 0;
    boolean sameJarClassFound = false;
    String currentFQN = null;
    boolean[] isOutOfScope = new boolean[cache.length]; // here we cache results of isInScope(scope, subClass) to avoid calculating it twice
    for (int i = 0; i < cache.length; i++) {
      ProgressManager.checkCanceled();

      PsiClass subClass = cache[i];
      if (subClass instanceof PsiAnonymousClass) {
        // we reached anonymous classes tail, process them all and exit
        if (!parameters.includeAnonymous()) {
          return flushCurrentGroup(cache, isOutOfScope, sameJarClassFound, groupStart, i, processor);
        }
      }
      if (!isInScope(scope, subClass)) {
        isOutOfScope[i] = true;
        continue;
      }

      String fqn = ReadAction.compute(subClass::getQualifiedName);

      if (currentFQN != null && Objects.equals(fqn, currentFQN)) {
        VirtualFile currentJarFile = getJarFile(subClass);
        if (baseClassJarFile == null) {
          baseClassJarFile = getJarFile(baseClass);
        }
        boolean fromSameJar = Comparing.equal(currentJarFile, baseClassJarFile);
        if (fromSameJar) {
          if (!processor.process(subClass)) return false;
          sameJarClassFound = true;
        }
      }
      else {
        currentFQN = fqn;
        // the end of the same-FQN group. Process only same-jar classes in subClasses[groupStart..i-1] group or the whole group if there were none.
        if (!flushCurrentGroup(cache, isOutOfScope, sameJarClassFound, groupStart, i, processor)) return false;
        groupStart = i;
        sameJarClassFound = false;
      }
    }

    return flushCurrentGroup(cache, isOutOfScope, sameJarClassFound, groupStart, cache.length, processor);
  }

  private static boolean flushCurrentGroup(PsiClass @NotNull [] cache,
                                           boolean @NotNull [] isOutOfScope,
                                           boolean sameJarClassFound,
                                           int groupStart,
                                           int afterGroup,
                                           @NotNull Processor<? super PsiClass> processor) {
    if (!sameJarClassFound) {
      for (int g = groupStart; g < afterGroup; g++) {
        ProgressManager.checkCanceled();
        if (!isOutOfScope[g] && !processor.process(cache[g])) {
          return false;
        }
      }
    }
    return true;
  }

  // true if processor should return true, false if the processor should return result of consumer.process(psiClass)
  private static boolean shortCircuitCandidate(@NotNull PsiClass psiClass) {
    return ReadAction.compute(() -> {
      if (psiClass.isInterface()) {
        return false;
      }
      final PsiClass superClass = psiClass.getSuperClass();
      if (superClass == null || !superClass.isValid()) {
        return true;
      }
      boolean isJavaLangObject = CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName());
      return !isJavaLangObject;
    });
  }
  private static PsiClass getClassToSearch(@NotNull DirectClassInheritorsSearch.SearchParameters parameters) {
    return ReadAction.compute(() -> (PsiClass)PsiUtil.preferCompiledElement(parameters.getClassToProcess()));
  }

  private static boolean isInScope(@NotNull SearchScope scope, @NotNull PsiClass subClass) {
    return ReadAction.compute(() -> PsiSearchScopeUtil.isInScope(scope, subClass));
  }

  // The list starts with non-anonymous classes, ends with anonymous sub classes
  // Classes grouped by their FQN. (Because among the same-named subclasses we should return only the same-jar ones, or all of them if there were none)
  private static PsiClass @NotNull [] getOrCalculateDirectSubClasses(@NotNull Project project,
                                                                     @NotNull PsiClass baseClass,
                                                                     @NotNull DirectClassInheritorsSearch.SearchParameters parameters) {
    ConcurrentMap<PsiClass, PsiClass[]> map = HighlightingCaches.getInstance(project).DIRECT_SUB_CLASSES;
    PsiClass[] cache = map.get(baseClass);
    if (cache != null) {
      return cache;
    }

    final String baseClassName = ReadAction.compute(baseClass::getName);
    if (StringUtil.isEmpty(baseClassName)) {
      return PsiClass.EMPTY_ARRAY;
    }
    cache = calculateDirectSubClasses(project, baseClass, baseClassName, parameters);
    // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
    if (ReadAction.compute(baseClass::isPhysical)) {
      cache = ConcurrencyUtil.cacheOrGet(map, baseClass, cache);
    }
    return cache;
  }

  private static <T> void processConcurrentlyIfTooMany(@NotNull Collection<? extends T> collection, @NotNull Processor<? super T> processor) {
    int size = collection.size();
    if (size == 0) {
      return;
    }
    if (size > 100) {
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(collection), ProgressIndicatorProvider.getGlobalProgressIndicator(), processor);
    }
    else {
      ContainerUtil.process(collection, processor);
    }
  }

  private static PsiClass @NotNull [] calculateDirectSubClasses(@NotNull Project project,
                                                                @NotNull PsiClass baseClass,
                                                                @NotNull String baseClassName,
                                                                @NotNull DirectClassInheritorsSearch.SearchParameters parameters) {
    SearchScope useScope;
    CompilerDirectHierarchyInfo info = performSearchUsingCompilerIndices(parameters, project);
    useScope = ReadAction.compute(() -> {
      SearchScope resultScope = PsiSearchHelper.getInstance(project).getUseScope(baseClass);
      if (info == null) return resultScope;
      return resultScope.intersectWith(info.getDirtyScope());
    });

    DumbService dumbService = DumbService.getInstance(project);
    GlobalSearchScope globalUseScope = ReadAction.compute(
      () -> new JavaSourceFilterScope(GlobalSearchScopeUtil.toGlobalSearchScope(useScope, project)));
    Collection<PsiReferenceList> candidates =
      dumbService.runReadActionInSmartMode(() -> JavaSuperClassNameOccurenceIndex.getInstance().get(baseClassName, project, globalUseScope));

    RelaxedDirectInheritorChecker checker = dumbService.runReadActionInSmartMode(() -> new RelaxedDirectInheritorChecker(baseClass));
    // memory/speed optimisation: it really is a map(string -> PsiClass or List<PsiClass>)
    final Map<String, Object> classesWithFqn = new HashMap<>();

    List<PsiAnonymousClass> anonymous = new ArrayList<>();

    processConcurrentlyIfTooMany(candidates,
       referenceList -> {
         ProgressManager.checkCanceled();
         dumbService.runReadActionInSmartMode(() -> {
           PsiElement parent = referenceList.getParent();
           PsiClass candidate;
           if (parent instanceof PsiClass && checker.checkInheritance(candidate = (PsiClass)parent)) {
             String fqn = candidate.getQualifiedName();

             synchronized (classesWithFqn) {
               if (candidate instanceof PsiAnonymousClass) {
                 anonymous.add((PsiAnonymousClass)candidate);
                 return;
               }

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
                 //noinspection unchecked
                 List<PsiClass> list = (List<PsiClass>)value;
                 list.add(candidate);
               }
             }
           }
         });

         return true;
       });

    final List<PsiClass> result = new ArrayList<>();
    synchronized (classesWithFqn) {
      for (Object value : classesWithFqn.values()) {
        if (value instanceof PsiClass) {
          result.add((PsiClass)value);
        }
        else {
          //noinspection unchecked
          List<PsiClass> list = (List<PsiClass>)value;
          result.addAll(list);
        }
      }
    }

    Collection<PsiAnonymousClass> anonymousCandidates =
      dumbService.runReadActionInSmartMode(() -> JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(baseClassName, project, globalUseScope));

    processConcurrentlyIfTooMany(anonymousCandidates, candidate-> {
      if (dumbService.runReadActionInSmartMode(() -> checker.checkInheritance(candidate))) {
        synchronized (result) {
          anonymous.add(candidate);
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
            synchronized (result) {
              anonymous.add(initializingClass); // it surely is an inheritor
            }
          }
        }
      }
    }

    if (info != null) {
      info.getHierarchyChildren().forEach(aClass -> {
        if (aClass instanceof PsiAnonymousClass) {
          anonymous.add((PsiAnonymousClass)aClass);
        }
        else if (aClass instanceof PsiClass) {
          result.add((PsiClass)aClass);
        }
      });
    }

    synchronized (result) {
      if (result.isEmpty() && anonymous.isEmpty()) return PsiClass.EMPTY_ARRAY;

      result.addAll(anonymous);
      return result.toArray(PsiClass.EMPTY_ARRAY);
    }
  }

  private static VirtualFile getJarFile(@NotNull PsiClass aClass) {
    return ReadAction.compute(() -> PsiUtil.getJarFile(aClass));
  }

  private static @Nullable CompilerDirectHierarchyInfo performSearchUsingCompilerIndices(@NotNull DirectClassInheritorsSearch.SearchParameters parameters,
                                                                                         @NotNull Project project) {
    SearchScope scope = parameters.getScope();
    if (!(scope instanceof GlobalSearchScope)) {
      return null;
    }

    CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstanceIfEnabled(project);
    if (compilerReferenceService == null) {
      return null;
    }
    return compilerReferenceService.getDirectInheritors(getClassToSearch(parameters), (GlobalSearchScope)scope, JavaFileType.INSTANCE);
  }
}

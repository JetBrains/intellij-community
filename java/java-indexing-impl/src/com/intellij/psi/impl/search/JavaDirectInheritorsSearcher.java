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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters parameters, @NotNull final Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();

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

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(project);
    Pair<List<PsiClass>, AtomicIntegerArray> pair = getOrCalculateDirectSubClasses(project, baseClass);
    List<PsiClass> result = pair.getFirst();
    AtomicIntegerArray isInheritorFlag = pair.getSecond();

    if (result.isEmpty()) {
      return true;
    }

    final VirtualFile jarFile = getJarFile(baseClass);
    // iterate by same-FQN groups. For each group process only same-jar subclasses, or all of them if they are all outside the jarFile.
    int groupStart = 0;
    boolean sameJarClassFound = false;
    for (int i = 0; i < result.size(); i++) {
      ProgressManager.checkCanceled();
      PsiClass subClass = result.get(i);
      if (subClass instanceof PsiAnonymousClass) {
        // we reached anonymous classes tail, process them all and exit
        if (!parameters.includeAnonymous()) {
          return true;
        }
        for (; i < result.size(); i++) {
          ProgressManager.checkCanceled();
          subClass = result.get(i);

          CheckResult checkResult = checkAndProcessCandidate(scope, subClass, parameters, baseClass, project, isInheritorFlag, i, consumer);
          if (checkResult == CheckResult.PROCESSED_ABORT) return false;
        }
        return true;
      }

      if (subClass == PsiUtil.NULL_PSI_CLASS) {
        // the end of the same-FQN group. Process only same-jar classes in the group or the whole group if there were none.
        if (!sameJarClassFound) {
          for (int g=groupStart; g<i; g++) {
            ProgressManager.checkCanceled();
            subClass = result.get(g);
            CheckResult checkResult = checkAndProcessCandidate(scope, subClass, parameters, baseClass, project, isInheritorFlag, g, consumer);
            if (checkResult == CheckResult.PROCESSED_ABORT) return false;
          }
        }
        groupStart = i+1;
        sameJarClassFound = false;
      }
      else {
        VirtualFile currentJarFile = getJarFile(subClass);
        boolean fromSameJar = Comparing.equal(currentJarFile, jarFile);
        if (!fromSameJar) continue;
        CheckResult checkResult = checkAndProcessCandidate(scope, subClass, parameters, baseClass, project, isInheritorFlag, i, consumer);
        if (checkResult == CheckResult.PROCESSED_ABORT) return false;
        if (checkResult == CheckResult.CANDIDATE_REJECTED) continue;
        sameJarClassFound = true;
      }
    }

    return true;
  }

  private enum CheckResult {
    CANDIDATE_REJECTED, PROCESSED_OK, PROCESSED_ABORT
  }

  @NotNull
  private static CheckResult checkAndProcessCandidate(@NotNull GlobalSearchScope scope,
                                                      @NotNull PsiClass candidate,
                                                      @NotNull DirectClassInheritorsSearch.SearchParameters parameters,
                                                      @NotNull PsiClass baseClass,
                                                      @NotNull Project project,
                                                      @NotNull AtomicIntegerArray isInheritorFlag, int i,
                                                      @NotNull Processor<PsiClass> consumer) {
    if (!isInScope(scope, candidate)) return CheckResult.CANDIDATE_REJECTED;
    if (!checkInheritance(parameters.isCheckInheritance(), baseClass, candidate, project, isInheritorFlag, i)) return CheckResult.CANDIDATE_REJECTED;
    return consumer.process(candidate) ? CheckResult.PROCESSED_OK : CheckResult.PROCESSED_ABORT;
  }

  private static boolean isInScope(@NotNull GlobalSearchScope scope, @NotNull PsiClass subClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> PsiSearchScopeUtil.isInScope(scope, subClass));
  }

  private static final int INHERITANCE_UNKNOWN = 0;
  private static final int INHERITANCE_YES = 1;
  private static final int INHERITANCE_NO = 2;

  // Returns pair ( list of direct subclasses, array of corresponding isInheritor flags )
  // The array initially contains INHERITANCE_UNKNOWN values, then the isInheritor() method result is cached in the array as INHERITANCE_YES or INHERITANCE_NO.
  // The list starts with non-anonymous classes, ends with anonymous sub classes
  // Regular classes grouped by their FQN. (Because among the same-named subclasses we should return only the same-jar ones, or all of them if there were none)
  // The groups are separated with NULL_PSI_CLASS
  @NotNull
  private static Pair<List<PsiClass>, AtomicIntegerArray> getOrCalculateDirectSubClasses(@NotNull Project project, @NotNull PsiClass baseClass) {
    Pair<List<PsiClass>, AtomicIntegerArray> cached = HighlightingCaches.getInstance(project).DIRECT_SUB_CLASSES.get(baseClass);
    if (cached != null) {
      return cached;
    }

    final String baseClassName = ApplicationManager.getApplication().runReadAction((Computable<String>)baseClass::getName);
    if (StringUtil.isEmpty(baseClassName)) {
      return Pair.create(Collections.emptyList(), new AtomicIntegerArray(0));
    }
    Pair<List<PsiClass>, AtomicIntegerArray> pair = calculateDirectSubClasses(project, baseClass, baseClassName);
    HighlightingCaches.getInstance(project).DIRECT_SUB_CLASSES.put(baseClass, pair);
    return pair;
  }

  @NotNull
  private static Pair<List<PsiClass>, AtomicIntegerArray> calculateDirectSubClasses(@NotNull Project project,
                                                                                    @NotNull PsiClass baseClass,
                                                                                    @NotNull String baseClassName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    Collection<PsiReferenceList> candidates =
      MethodUsagesSearcher.resolveInReadAction(project, () -> JavaSuperClassNameOccurenceIndex.getInstance().get(baseClassName, project, allScope));

    Map<String, List<PsiClass>> classes = new HashMap<>();
    int count = 0;

    for (final PsiReferenceList referenceList : candidates) {
      ProgressManager.checkCanceled();
      final PsiClass candidate = (PsiClass)ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)referenceList::getParent);

      String fqn = ApplicationManager.getApplication().runReadAction((Computable<String>)candidate::getQualifiedName);
      List<PsiClass> list = classes.get(fqn);
      if (list == null) {
        list = new SmartList<>();
        classes.put(fqn, list);
      }
      list.add(candidate);
      count++;
    }

    Collection<PsiAnonymousClass> anonymousCandidates =
      MethodUsagesSearcher.resolveInReadAction(project, () -> JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(baseClassName, project, allScope));

    List<PsiClass> result = new ArrayList<>(count + classes.size() + anonymousCandidates.size() + 1);
    for (Map.Entry<String, List<PsiClass>> entry : classes.entrySet()) {
      result.addAll(entry.getValue());
      result.add(PsiUtil.NULL_PSI_CLASS);
    }

    result.addAll(anonymousCandidates);

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
            result.add(initializingClass);
          }
        }
      }
    }

    return Pair.create(result, new AtomicIntegerArray(result.size()));
  }

  private static boolean checkInheritance(boolean checkInheritance,
                                          @NotNull PsiClass baseClass,
                                          @NotNull PsiClass candidate,
                                          @NotNull Project project,
                                          @NotNull AtomicIntegerArray isInheritorFlags,
                                          int i) {
    if (!checkInheritance) return true;
    int cachedFlag = isInheritorFlags.get(i);
    if (cachedFlag == INHERITANCE_YES) return true;
    if (cachedFlag == INHERITANCE_NO) return false;
    assert cachedFlag == INHERITANCE_UNKNOWN;
    boolean isReallyInherited = MethodUsagesSearcher.resolveInReadAction(project, () -> candidate.isInheritor(baseClass, false));
    isInheritorFlags.set(i, isReallyInherited ? INHERITANCE_YES : INHERITANCE_NO);
    return isReallyInherited;
  }

  private static VirtualFile getJarFile(@NotNull PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> PsiUtil.getJarFile(aClass));
  }
}

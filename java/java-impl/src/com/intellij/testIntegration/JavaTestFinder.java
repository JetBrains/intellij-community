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
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class JavaTestFinder implements TestFinder {
  public PsiClass findSourceElement(@NotNull PsiElement element) {
    return TestIntegrationUtils.findOuterClass(element);
  }

  @NotNull
  public Collection<PsiElement> findClassesForTest(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope = getSearchScope(element, true);

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(element.getProject());

    List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<>();
    for (Pair<String, Integer> eachNameWithWeight : TestFinderHelper.collectPossibleClassNamesWithWeights(klass.getName())) {
      for (PsiClass eachClass : cache.getClassesByName(eachNameWithWeight.first, scope)) {
        if (isTestSubjectClass(eachClass)) {
          classesWithWeights.add(Pair.create(eachClass, eachNameWithWeight.second));
        }
      }
    }

    return TestFinderHelper.getSortedElements(classesWithWeights, false);
  }

  /**
   * @deprecated {@link JavaTestFinder#getSearchScope(com.intellij.psi.PsiElement, boolean)}
   */
  protected GlobalSearchScope getSearchScope(PsiElement element) {
    return getSearchScope(element, true);
  }

  protected GlobalSearchScope getSearchScope(PsiElement element, boolean dependencies) {
    final Module module = getModule(element);
    if (module != null) {
      return dependencies ? GlobalSearchScope.moduleWithDependenciesScope(module) 
                          : GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      return GlobalSearchScope.projectScope(element.getProject());
    }
  }

  protected boolean isTestSubjectClass(PsiClass klass) {
    if (klass.isAnnotationType() || 
        TestFrameworks.getInstance().isTestClass(klass) ||
        !klass.isPhysical()) {
      return false;
    }
    return true;
  }

  @NotNull
  public Collection<PsiElement> findTestsForClass(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<>();
    Processor<Pair<? extends PsiNamedElement, Integer>> processor =
      Processors.cancelableCollectProcessor(classesWithProximities);
    collectTests(klass, processor);

    return TestFinderHelper.getSortedElements(classesWithProximities, true);
  }

  private boolean collectTests(PsiClass klass, Processor<Pair<? extends PsiNamedElement, Integer>> processor) {
    GlobalSearchScope scope = getSearchScope(klass, false);

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(klass.getProject());

    String klassName = klass.getName();
    Pattern pattern = Pattern.compile(".*" + StringUtil.escapeToRegexp(klassName) + ".*", Pattern.CASE_INSENSITIVE);

    HashSet<String> names = new HashSet<>();
    cache.getAllClassNames(names);
    for (String eachName : names) {
      if (pattern.matcher(eachName).matches()) {
        for (PsiClass eachClass : cache.getClassesByName(eachName, scope)) {
          if (isTestClass(eachClass, klass)) {
            if (!processor.process(Pair.create(eachClass, TestFinderHelper.calcTestNameProximity(klassName, eachName)))) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  protected boolean isTestClass(PsiClass eachClass, PsiClass klass) {
    final TestFrameworks frameworks = TestFrameworks.getInstance();
    return eachClass.isPhysical() && (frameworks.isTestClass(eachClass) || eachClass != klass && frameworks.isPotentialTestClass(eachClass));
  }

  @Nullable
  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    VirtualFile file = PsiUtilCore.getVirtualFile(element);
    return file == null ? null : index.getModuleForFile(file);
  }

  public boolean isTest(@NotNull PsiElement element) {
    return TestIntegrationUtils.isTest(element);
  }
}

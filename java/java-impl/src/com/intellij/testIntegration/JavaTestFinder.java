/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class JavaTestFinder implements TestFinder {
  public PsiClass findSourceElement(@NotNull PsiElement element) {
    return TestIntegrationUtils.findOuterClass(element);
  }

  @NotNull
  public Collection<PsiElement> findClassesForTest(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();

    List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<Pair<? extends PsiNamedElement, Integer>>();
    for (Pair<String, Integer> eachNameWithWeight : TestFinderHelper.collectPossibleClassNamesWithWeights(klass.getName())) {
      for (PsiClass eachClass : cache.getClassesByName(eachNameWithWeight.first, scope)) {
        if (isTestSubjectClass(eachClass)) {
          classesWithWeights.add(new Pair<PsiClass, Integer>(eachClass, eachNameWithWeight.second));
        }
      }
    }

    return TestFinderHelper.getSortedElements(classesWithWeights, false);
  }

  private static boolean isTestSubjectClass(PsiClass klass) {
    if (klass.isEnum()
        || klass.isInterface()
        || klass.isAnnotationType()
        || TestUtil.isTestClass(klass)) {
      return false;
    }
    return true;
  }

  @NotNull
  public Collection<PsiElement> findTestsForClass(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();

    String klassName = klass.getName();
    Pattern pattern = Pattern.compile(".*" + klassName + ".*");

    List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<Pair<? extends PsiNamedElement, Integer>>();

    HashSet<String> names = new HashSet<String>();
    cache.getAllClassNames(names);
    for (String eachName : names) {
      if (pattern.matcher(eachName).matches()) {
        for (PsiClass eachClass : cache.getClassesByName(eachName, scope)) {
          if (TestUtil.isTestClass(eachClass)) {
            classesWithProximities.add(
                new Pair<PsiClass, Integer>(eachClass, TestFinderHelper.calcTestNameProximity(klassName, eachName)));
          }
        }
      }
    }

    return TestFinderHelper.getSortedElements(classesWithProximities, true);
  }

  @Nullable
  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(@NotNull PsiElement element) {
    return TestIntegrationUtils.isTest(element);
  }
}

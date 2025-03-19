// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ImplicitClassSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaTestFinder implements TestFinder {
  @Override
  public PsiClass findSourceElement(@NotNull PsiElement element) {
    return TestIntegrationUtils.findOuterClass(element);
  }

  @Override
  public @NotNull Collection<PsiElement> findClassesForTest(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope = getSearchScope(element, true);

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(element.getProject());

    List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<>();
    for (Pair<String, Integer> eachNameWithWeight : TestFinderHelper.collectPossibleClassNamesWithWeights(klass.getName())) {
      List<PsiClass> explicitClasses = Arrays.asList(cache.getClassesByName(eachNameWithWeight.first, scope));
      Collection<PsiImplicitClass> implicitClasses =
        ImplicitClassSearch.search(eachNameWithWeight.first, klass.getProject(), scope).findAll();
      for (PsiClass eachClass : ContainerUtil.concat(explicitClasses, implicitClasses)) {
        if (isTestSubjectClass(eachClass)) {
          classesWithWeights.add(Pair.create(eachClass, eachNameWithWeight.second));
        }
      }
    }

    return TestFinderHelper.getSortedElements(classesWithWeights, false);
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

  @Override
  public @NotNull Collection<PsiElement> findTestsForClass(@NotNull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<>();
    Processor<Pair<? extends PsiNamedElement, Integer>> processor =
      Processors.cancelableCollectProcessor(classesWithProximities);
    collectTests(klass, processor);

    return TestFinderHelper.getSortedElements(classesWithProximities, true);
  }

  private void collectTests(PsiClass klass, Processor<? super Pair<? extends PsiNamedElement, Integer>> processor) {
    GlobalSearchScope scope = getSearchScope(klass, false);

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(klass.getProject());

    String klassName = klass.getName();
    assert klassName != null;
    klassName = StringUtil.trimStart(klassName, JavaCodeStyleSettings.getInstance(klass.getContainingFile()).SUBCLASS_NAME_PREFIX);
    klassName = StringUtil.trimEnd(klassName, JavaCodeStyleSettings.getInstance(klass.getContainingFile()).SUBCLASS_NAME_SUFFIX);
    if (klassName.isEmpty()) {
      klassName = klass.getName();
    }
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + klassName, NameUtil.MatchingCaseSensitivity.NONE);
    for (String eachName : ContainerUtil.newHashSet(cache.getAllClassNames())) {
      if (matcher.matches(eachName)) {
        for (PsiClass eachClass : cache.getClassesByName(eachName, scope)) {
          if (isTestClass(eachClass, klass) && !processor.process(Pair.create(eachClass, TestFinderHelper.calcTestNameProximity(klassName, eachName)))) {
            return;
          }
        }
      }
    }
  }

  protected boolean isTestClass(PsiClass eachClass, PsiClass klass) {
    final TestFrameworks frameworks = TestFrameworks.getInstance();
    return eachClass.isPhysical() && (frameworks.isTestClass(eachClass) || eachClass != klass && frameworks.isPotentialTestClass(eachClass));
  }

  private static @Nullable Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    VirtualFile file = PsiUtilCore.getVirtualFile(element);
    return file == null ? null : index.getModuleForFile(file);
  }

  @Override
  public boolean isTest(@NotNull PsiElement element) {
    return TestIntegrationUtils.isTest(element);
  }
}

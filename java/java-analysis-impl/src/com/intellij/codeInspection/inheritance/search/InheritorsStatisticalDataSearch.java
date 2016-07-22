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
package com.intellij.codeInspection.inheritance.search;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class InheritorsStatisticalDataSearch {

  /**
   * search for most used inheritors of superClass in scope
   *
   * @param aClass          - class that excluded from inheritors of superClass
   * @param minPercentRatio - head volume
   * @return - search results in relevant ordering (frequency descent)
   */
  public static List<InheritorsStatisticsSearchResult> search(final @NotNull PsiClass superClass,
                                          final @NotNull PsiClass aClass,
                                          final @NotNull GlobalSearchScope scope,
                                          final int minPercentRatio) {
    final String superClassName = superClass.getName();
    final String aClassName = aClass.getName();
    final Set<String> disabledNames = new HashSet<>();
    disabledNames.add(aClassName);
    disabledNames.add(superClassName);
    final Set<InheritorsCountData> collector = new TreeSet<>();
    final Couple<Integer> collectingResult = collectInheritorsInfo(superClass, collector, disabledNames);
    final int allAnonymousInheritors = collectingResult.getSecond();
    final int allInheritors = collectingResult.getFirst() + allAnonymousInheritors - 1;

    final List<InheritorsStatisticsSearchResult> result = new ArrayList<>();

    Integer firstPercent = null;
    for (final InheritorsCountData data : collector) {
      final int inheritorsCount = data.getInheritorsCount();
      if (inheritorsCount < allAnonymousInheritors) {
        break;
      }
      final int percent = (inheritorsCount * 100) / allInheritors;
      if (percent < 1) {
        break;
      }
      if (firstPercent == null) {
        firstPercent = percent;
      }
      else if (percent * minPercentRatio < firstPercent) {
        break;
      }

      final PsiClass psiClass = data.getPsiClass();
      final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
      if (file != null && scope.contains(file)) {
        result.add(new InheritorsStatisticsSearchResult(psiClass, percent));
      }
    }
    return result;
  }

  private static Couple<Integer> collectInheritorsInfo(final PsiClass superClass,
                                                              final Set<InheritorsCountData> collector,
                                                              final Set<String> disabledNames) {
    return collectInheritorsInfo(superClass, collector, disabledNames, new HashSet<>(), new HashSet<>());
  }

  private static Couple<Integer> collectInheritorsInfo(final PsiClass aClass,
                                                              final Set<InheritorsCountData> collector,
                                                              final Set<String> disabledNames,
                                                              final Set<String> processedElements,
                                                              final Set<String> allNotAnonymousInheritors) {
    final String className = aClass.getName();
    if (!processedElements.add(className)) return Couple.of(0, 0);

    final MyInheritorsInfoProcessor processor = new MyInheritorsInfoProcessor(collector, disabledNames, processedElements);
    DirectClassInheritorsSearch.search(aClass).forEach(processor);

    allNotAnonymousInheritors.addAll(processor.getAllNotAnonymousInheritors());

    final int allInheritorsCount = processor.getAllNotAnonymousInheritors().size() + processor.getAnonymousInheritorsCount();
    if (!aClass.isInterface() && allInheritorsCount != 0 && !disabledNames.contains(className)) {
      collector.add(new InheritorsCountData(aClass, allInheritorsCount));
    }
    return Couple.of(allNotAnonymousInheritors.size(), processor.getAnonymousInheritorsCount());
  }

  private static class MyInheritorsInfoProcessor implements Processor<PsiClass> {
    private final Set<InheritorsCountData> myCollector;
    private final Set<String> myDisabledNames;
    private final Set<String> myProcessedElements;
    private final Set<String> myAllNotAnonymousInheritors;

    private MyInheritorsInfoProcessor(Set<InheritorsCountData> collector, Set<String> disabledNames, Set<String> processedElements) {
      myCollector = collector;
      myDisabledNames = disabledNames;
      myProcessedElements = processedElements;
      myAllNotAnonymousInheritors = new HashSet<>();
    }

    private int myAnonymousInheritorsCount;

    private Set<String> getAllNotAnonymousInheritors() {
      return myAllNotAnonymousInheritors;
    }

    private int getAnonymousInheritorsCount() {
      return myAnonymousInheritorsCount;
    }

    @Override
    public boolean process(final PsiClass psiClass) {
      final String inheritorName = psiClass.getName();
      if (inheritorName == null) {
        myAnonymousInheritorsCount++;
      }
      else {
        final Couple<Integer> res = collectInheritorsInfo(psiClass,
                                                          myCollector,
                                                          myDisabledNames,
                                                          myProcessedElements,
                                                          myAllNotAnonymousInheritors);
        myAnonymousInheritorsCount += res.getSecond();
        if (!psiClass.isInterface()) {
          myAllNotAnonymousInheritors.add(inheritorName);
        }
      }
      return true;
    }
  }
}

package com.intellij.codeInspection.inheritance.search;

import com.intellij.openapi.util.Pair;
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
    final Set<String> disabledNames = new HashSet<String>();
    disabledNames.add(aClassName);
    disabledNames.add(superClassName);
    final Set<InheritorsCountData> collector = new TreeSet<InheritorsCountData>();
    final Pair<Integer, Integer> collectingResult = collectInheritorsInfo(superClass, collector, disabledNames);
    final int allAnonymousInheritors = collectingResult.getSecond();
    final int allInheritors = collectingResult.getFirst() + allAnonymousInheritors - 1;

    final List<InheritorsStatisticsSearchResult> result = new ArrayList<InheritorsStatisticsSearchResult>();

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

  private static Pair<Integer, Integer> collectInheritorsInfo(final PsiClass superClass,
                                                              final Set<InheritorsCountData> collector,
                                                              final Set<String> disabledNames) {
    return collectInheritorsInfo(superClass, collector, disabledNames, new HashSet<String>(), new HashSet<String>());
  }

  private static Pair<Integer, Integer> collectInheritorsInfo(final PsiClass aClass,
                                                              final Set<InheritorsCountData> collector,
                                                              final Set<String> disabledNames,
                                                              final Set<String> processedElements,
                                                              final Set<String> allNotAnonymousInheritors) {
    final String className = aClass.getName();
    if (!processedElements.add(className)) return Pair.create(0, 0);

    final MyInheritorsInfoProcessor processor = new MyInheritorsInfoProcessor(collector, disabledNames, processedElements);
    DirectClassInheritorsSearch.search(aClass).forEach(processor);

    allNotAnonymousInheritors.addAll(processor.getAllNotAnonymousInheritors());

    final int allInheritorsCount = processor.getAllNotAnonymousInheritors().size() + processor.getAnonymousInheritorsCount();
    if (!aClass.isInterface() && allInheritorsCount != 0 && !disabledNames.contains(className)) {
      collector.add(new InheritorsCountData(aClass, allInheritorsCount));
    }
    return Pair.create(allNotAnonymousInheritors.size(), processor.getAnonymousInheritorsCount());
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
      myAllNotAnonymousInheritors = new HashSet<String>();
    }

    private int myAnonymousInheritorsCount = 0;

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
        final Pair<Integer, Integer> res =
          collectInheritorsInfo(psiClass, myCollector, myDisabledNames, myProcessedElements, myAllNotAnonymousInheritors);
        myAnonymousInheritorsCount += res.getSecond();
        if (!psiClass.isInterface()) {
          myAllNotAnonymousInheritors.add(inheritorName);
        }
      }
      return true;
    }
  }
}

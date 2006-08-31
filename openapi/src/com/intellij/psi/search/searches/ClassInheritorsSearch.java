/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.*;
import gnu.trove.THashSet;

import java.util.Collection;

/**
 * @author max
 */
public class ClassInheritorsSearch extends QueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.searches.ClassInheritorsSearch");

  public static ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  static {
    INSTANCE.registerExecutor(new QueryExecutor<PsiClass, SearchParameters>() {
      public boolean execute(final SearchParameters p, final Processor<PsiClass> consumer) {
        final PsiClass baseClass = p.getClassToProcess();
        final SearchScope searchScope = p.getScope();

        LOG.assertTrue(searchScope != null);

        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.pushState();
          String className = baseClass.getName();
          progress.setText(className != null ?
                           PsiBundle.message("psi.search.inheritors.of.class.progress", className) :
                           PsiBundle.message("psi.search.inheritors.progress"));
        }

        Collection<PsiClass> processed = new THashSet<PsiClass>();
        processed.add(baseClass);
        boolean result = processInheritors(consumer,
                                           baseClass,
                                           searchScope,
                                           p.isCheckDeep(),
                                           processed,
                                           p.isCheckInheritance());

        if (progress != null) {
          progress.popState();
        }

        return result;
      }
    });
  }

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;

    public SearchParameters(final PsiClass aClass, SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
    }

    public PsiClass getClassToProcess() {
      return myClass;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }
  }

  private ClassInheritorsSearch() {}

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope, checkDeep, checkInheritance));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, final boolean checkDeep) {
    return search(aClass, scope, checkDeep, true);
  }

  public static Query<PsiClass> search(final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, aClass.getUseScope(), checkDeep);
  }

  public static Query<PsiClass> search(final PsiClass aClass) {
    return search(aClass, true);
  }

  private static boolean processInheritors(final Processor<PsiClass> consumer,
                                           final PsiClass baseClass,
                                           final SearchScope searchScope,
                                           final boolean checkDeep,
                                           final Collection<PsiClass> processed,
                                           final boolean checkInheritance) {
    LOG.assertTrue(searchScope != null);

    if (baseClass instanceof PsiAnonymousClass) return true;
    if (baseClass.hasModifierProperty(PsiModifier.FINAL)) return true;

    if ("java.lang.Object".equals(baseClass.getQualifiedName())) { // special case
      // TODO!
    }

    final PsiManager psiManager = PsiManager.getInstance(baseClass.getProject());

    DirectClassInheritorsSearch.search(baseClass).forEach(new Processor<PsiClass>() {
      public boolean process(final PsiClass candidate) {
        if (checkInheritance || (checkDeep && !(candidate instanceof PsiAnonymousClass))) {
          if (!candidate.isInheritor(baseClass, false)) return true;

          if (!processed.add(candidate)) return true;
        }

        if (candidate instanceof PsiAnonymousClass) {
          if (!consumer.process(candidate)) return false;
        }
        else {
          if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
            if (searchScope instanceof GlobalSearchScope) {
              String qName = candidate.getQualifiedName();
              if (qName != null) {
                PsiClass[] candidateClasses = psiManager.findClasses(qName, (GlobalSearchScope)searchScope);
                if (ArrayUtil.find(candidateClasses, candidate) == -1) return true;
              }
            }
            if (!consumer.process(candidate)) return false;
          }

          if (checkDeep) {
            if (!processInheritors(consumer, candidate, searchScope, checkDeep, processed, checkInheritance)) return false;
          }
        }

        return true;
      }
    });


    return true;
  }
}

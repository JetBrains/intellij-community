/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import gnu.trove.THashSet;

import java.util.Collection;

/**
 * @author max
 */
public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
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
                                           p.isCheckInheritance(),
                                           p.isIncludeAnonymous());

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
    private final boolean myIncludeAnonymous;

    public SearchParameters(final PsiClass aClass, SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      myIncludeAnonymous = includeAnonymous;
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

    public boolean isIncludeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private ClassInheritorsSearch() {}

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope, checkDeep, checkInheritance, includeAnonymous));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    return search(aClass, scope, checkDeep, checkInheritance, true);
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
                                           final boolean checkInheritance,
                                           final boolean includeAnonymous) {
    LOG.assertTrue(searchScope != null);

    if (baseClass instanceof PsiAnonymousClass) return true;

    boolean isFinal = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return Boolean.valueOf(baseClass.hasModifierProperty(PsiModifier.FINAL));
      }
    }).booleanValue();

    if (isFinal) return true;

    /* TODO
    if ("java.lang.Object".equals(baseClass.getQualifiedName())) { // special case
      // TODO!
    }
    */

    final PsiManager psiManager = PsiManager.getInstance(baseClass.getProject());

    DirectClassInheritorsSearch.search(baseClass, GlobalSearchScope.allScope(baseClass.getProject()), includeAnonymous).forEach(new Processor<PsiClass>() {
      public boolean process(final PsiClass candidate) {
        final Ref<Boolean> result = new Ref<Boolean>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (checkInheritance || (checkDeep && !(candidate instanceof PsiAnonymousClass))) {
              if (!candidate.isInheritor(baseClass, false) || !processed.add(candidate)) {
                result.set(true);
                return;
              }
            }

            if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
              if (candidate instanceof PsiAnonymousClass) {
                result.set(consumer.process(candidate));
              }
              else {
                if (searchScope instanceof GlobalSearchScope) {
                  String qName = candidate.getQualifiedName();
                  if (qName != null) {
                    PsiClass[] candidateClasses = psiManager.findClasses(qName, (GlobalSearchScope)searchScope);
                    if (ArrayUtil.find(candidateClasses, candidate) == -1) result.set(true);
                  }
                }
                if (!consumer.process(candidate)) result.set(false);
              }
            }
          }
        });
        if (!result.isNull()) return result.get();

        if (checkDeep) {
          if (!processInheritors(consumer, candidate, searchScope, checkDeep, processed, checkInheritance, includeAnonymous)) return false;
        }

        return true;
      }
    });


    return true;
  }
}

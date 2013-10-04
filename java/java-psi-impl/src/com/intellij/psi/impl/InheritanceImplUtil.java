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
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class InheritanceImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.InheritanceImplUtil");

  public static boolean isInheritor(@NotNull final PsiClass candidateClass, @NotNull PsiClass baseClass, final boolean checkDeep) {
    if (baseClass instanceof PsiAnonymousClass) return false;
    if (!checkDeep) return isInheritor(candidateClass, baseClass, false, null);

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(candidateClass.getQualifiedName())) return false;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())) return true;
    Map<PsiClass, Boolean> map = CachedValuesManager.
      getCachedValue(candidateClass, new CachedValueProvider<Map<PsiClass, Boolean>>() {
        @Nullable
        @Override
        public Result<Map<PsiClass, Boolean>> compute() {
          final Map<PsiClass, Boolean> map = new ConcurrentHashMap<PsiClass, Boolean>();
          return Result.create(map, candidateClass, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
      });

    Boolean computed = map.get(baseClass);
    if (computed == null) {
      computed = isInheritor(candidateClass, baseClass, true, null);
      map.put(baseClass, computed);
    }
    return computed;
  }

  private static boolean isInheritor(@NotNull PsiClass candidateClass, @NotNull PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    if (candidateClass instanceof PsiAnonymousClass) {
      final PsiClass baseCandidateClass = ((PsiAnonymousClass)candidateClass).getBaseClassType().resolve();
      return baseCandidateClass != null && InheritanceUtil.isInheritorOrSelf(baseCandidateClass, baseClass, checkDeep);
    }
    PsiManager manager = candidateClass.getManager();
    /* //TODO fix classhashprovider so it doesn't use class qnames only
    final ClassHashProvider provider = getHashProvider((PsiManagerImpl) manager);
    if (checkDeep && provider != null) {
      try {
        return provider.isInheritor(baseClass, candidateClass);
      }
      catch (ClassHashProvider.OutOfRangeException e) {
      }
    }
    */
    if(checkDeep && LOG.isDebugEnabled()){
      LOG.debug("Using uncached version for " + candidateClass.getQualifiedName() + " and " + baseClass);
    }

    @NonNls final String baseName = baseClass.getName();
    if ("Object".equals(baseName)) {
      PsiClass objectClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, candidateClass.getResolveScope());
      if (manager.areElementsEquivalent(baseClass, objectClass)) {
        if (manager.areElementsEquivalent(candidateClass, objectClass)) return false;
        if (checkDeep || candidateClass.isInterface()) return true;
        return manager.areElementsEquivalent(candidateClass.getSuperClass(), objectClass);
      }
    }

    if (!checkDeep) {
      final boolean cInt = candidateClass.isInterface();
      final boolean bInt = baseClass.isInterface();

      if (candidateClass instanceof PsiCompiledElement) {
        String baseQName = baseClass.getQualifiedName();
        if (baseQName == null) return false;

        GlobalSearchScope scope = candidateClass.getResolveScope();
        if (cInt == bInt && checkReferenceListWithQualifiedNames(baseQName, candidateClass.getExtendsList(), manager, scope)) return true;
        return bInt && !cInt && checkReferenceListWithQualifiedNames(baseQName, candidateClass.getImplementsList(), manager, scope);
      }
      if (cInt == bInt) {
        for (PsiClassType type : candidateClass.getExtendsListTypes()) {
          if (Comparing.equal(type.getClassName(), baseName)) {
            if (manager.areElementsEquivalent(baseClass, type.resolve())) {
              return true;
            }
          }
        }
      }
      else if (!cInt) {
        for (PsiClassType type : candidateClass.getImplementsListTypes()) {
          if (Comparing.equal(type.getClassName(), baseName)) {
            if (manager.areElementsEquivalent(baseClass, type.resolve())) {
              return true;
            }
          }
        }
      }

      return false;
    }

    return isInheritorWithoutCaching(candidateClass, baseClass, checkDeep, checkedClasses);
  }

  private static boolean checkReferenceListWithQualifiedNames(final String baseQName, final PsiReferenceList extList, final PsiManager manager,
                                                              final GlobalSearchScope scope) {
    if (extList != null) {
      final PsiJavaCodeReferenceElement[] refs = extList.getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : refs) {
        if (Comparing.equal(PsiNameHelper.getQualifiedClassName(ref.getQualifiedName(), false), baseQName) && JavaPsiFacade
          .getInstance(manager.getProject()).findClass(baseQName, scope) != null)
          return true;
      }
    }
    return false;
  }

  private static boolean isInheritorWithoutCaching(PsiClass aClass, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    PsiManager manager = aClass.getManager();
    if (manager.areElementsEquivalent(aClass, baseClass)) return false;

    if (aClass.isInterface() && !baseClass.isInterface()) {
      return false;
    }

    //if (PsiUtil.hasModifierProperty(baseClass, PsiModifier.FINAL)) {
    //  return false;
    //}

    if (checkDeep) {
      if (checkedClasses == null) {
        checkedClasses = new THashSet<PsiClass>();
      }
      checkedClasses.add(aClass);
    }

    if (!aClass.isInterface() && baseClass.isInterface()) {
      if (checkDeep && checkInheritor(aClass.getSuperClass(), baseClass, checkDeep, checkedClasses)) {
        return true;
      }
      return checkInheritor(aClass.getInterfaces(), baseClass, checkDeep, checkedClasses);

    }
    else {
      return checkInheritor(aClass.getSupers(), baseClass, checkDeep, checkedClasses);
    }
  }

  private static boolean checkInheritor(PsiClass[] supers, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    for (PsiClass aSuper : supers) {
      if (checkInheritor(aSuper, baseClass, checkDeep, checkedClasses)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkInheritor(PsiClass aClass, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    ProgressIndicatorProvider.checkCanceled();
    if (aClass != null) {
      PsiManager manager = baseClass.getManager();
      if (manager.areElementsEquivalent(baseClass, aClass)) {
        return true;
      }
      if (checkedClasses != null && checkedClasses.contains(aClass)) { // to prevent infinite recursion
        return false;
      }
      if (checkDeep) {
        if (isInheritor(aClass, baseClass, checkDeep, checkedClasses)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInheritorDeep(@NotNull PsiClass candidateClass, @NotNull PsiClass baseClass, @Nullable final PsiClass classToByPass) {
    if (baseClass instanceof PsiAnonymousClass) {
      return false;
    }

    Set<PsiClass> checkedClasses = null;
    if (classToByPass != null) {
      checkedClasses = new HashSet<PsiClass>();
      checkedClasses.add(classToByPass);
    }
    return isInheritor(candidateClass, baseClass, true, checkedClasses);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.util.*;

/**
 * To avoid expensive super type resolve, if there's only one suitable class with the required name in the project anyway
 */
public class RelaxedDirectInheritorChecker {
  private final String myBaseClassName;
  private final PsiClass myBaseClass;
  private final VolatileNotNullLazyValue<PsiClass[]> myClasses;
  private final VolatileNotNullLazyValue<Boolean> myHasGlobalAmbiguities;
  private final ProjectFileIndex myFileIndex;

  public RelaxedDirectInheritorChecker(@NotNull PsiClass baseClass) {
    myBaseClass = baseClass;
    myBaseClassName = Objects.requireNonNull(baseClass.getName());
    myClasses = VolatileNotNullLazyValue.createValue(() -> getClassesByName(myBaseClass.getProject(), myBaseClassName));
    myHasGlobalAmbiguities = VolatileNotNullLazyValue.createValue(() -> hasAmbiguities(JBIterable.of(myClasses.getValue())));
    myFileIndex = ProjectFileIndex.getInstance(myBaseClass.getProject());
  }

  @NotNull
  private static PsiClass[] getClassesByName(Project project, String name) {
    Map<String, Reference<PsiClass[]>> cache = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<String, Reference<PsiClass[]>> map = ContainerUtil.newConcurrentMap();
      return CachedValueProvider.Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
    PsiClass[] result = SoftReference.dereference(cache.get(name));
    if (result == null) {
      result = PsiShortNamesCache.getInstance(project).getClassesByName(name, GlobalSearchScope.allScope(project));
      cache.put(name, new SoftReference<>(result));
    }
    return result;

  }

  private static boolean hasAmbiguities(Iterable<PsiClass> classes) {
    int locals = 0;
    Set<String> qNames = new HashSet<>();
    for (PsiClass psiClass : classes) {
      String qName = psiClass.getQualifiedName();
      if (qName == null) {
        locals++;
        if (locals > 1) break;
      } else {
        qNames.add(qName);
        if (qNames.size() > 1) break;
      }
    }
    return locals + qNames.size() > 1;
  }

  public boolean checkInheritance(@NotNull PsiClass inheritorCandidate) {
    if (!inheritorCandidate.isValid() || !myBaseClass.isValid()) return false;
    if (myFileIndex.isInSourceContent(inheritorCandidate.getContainingFile().getVirtualFile())) {
      if (!myHasGlobalAmbiguities.getValue()) {
        return true;
      }

      GlobalSearchScope scope = inheritorCandidate.getResolveScope();
      List<PsiClass> accessible = ContainerUtil.findAll(myClasses.getValue(), base ->
        PsiSearchScopeUtil.isInScope(scope, base) && isAccessibleLight(inheritorCandidate, base));
      if (!hasAmbiguities(accessible)) {
        return accessible.contains(myBaseClass);
      }
    }

    return inheritorCandidate.isInheritor(myBaseClass, false);
  }

  private static boolean isAccessibleLight(@NotNull PsiClass inheritorCandidate, @NotNull PsiClass base) {
    PsiModifierList modifierList = base.getModifierList();
    if (modifierList != null && PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      return true; // requires hierarchy checks => resolve
    }
    
    return JavaResolveUtil.isAccessible(base, base.getContainingClass(), modifierList, inheritorCandidate, null, null);
  }
}

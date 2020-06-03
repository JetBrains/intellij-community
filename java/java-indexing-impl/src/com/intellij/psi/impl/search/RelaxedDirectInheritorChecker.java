// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
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
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * To avoid expensive super type resolve, if there's only one suitable class with the required name in the project anyway
 */
public class RelaxedDirectInheritorChecker {
  private final String myBaseClassName;
  private final PsiClass myBaseClass;
  private final VolatileNotNullLazyValue<Pair<PsiClass[], Boolean>> myClasses;
  private final ProjectFileIndex myFileIndex;

  public RelaxedDirectInheritorChecker(@NotNull PsiClass baseClass) {
    myBaseClass = baseClass;
    myBaseClassName = Objects.requireNonNull(baseClass.getName());
    myClasses = VolatileNotNullLazyValue.createValue(() -> getClassesAndTheirAmbiguities(myBaseClass.getProject(), myBaseClassName));
    myFileIndex = ProjectFileIndex.getInstance(myBaseClass.getProject());
  }

  private static @NotNull Pair<PsiClass[], Boolean> getClassesAndTheirAmbiguities(@NotNull Project project, @NotNull String classShortName) {
    Map<String, Reference<Pair<PsiClass[],Boolean>>> cache = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<String, Reference<Pair<PsiClass[], Boolean>>> map = new ConcurrentHashMap<>();
      return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
    });
    Pair<PsiClass[], Boolean> result = SoftReference.dereference(cache.get(classShortName));
    if (result == null) {
      PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(classShortName, GlobalSearchScope.allScope(project));
      boolean ambiguities = hasAmbiguities(Arrays.asList(classes));
      result = Pair.create(classes, ambiguities);
      cache.put(classShortName, new SoftReference<>(result));
    }

    return result;
  }

  // false if all classes in the list have the same FQN
  private static boolean hasAmbiguities(@NotNull List<? extends PsiClass> classes) {
    int locals = 0;
    String theFQN = null;
    for (PsiClass psiClass : classes) {
      String qName = psiClass.getQualifiedName();
      if (qName == null) {
        locals++;
        if (locals > 1) return true;
      }
      else if (theFQN == null) {
        theFQN = qName;
      }
      else if (!theFQN.equals(qName)) {
        return true;
      }
    }
    return locals == 1 && theFQN != null;
  }

  /**
   * This assumes that {@code inheritorCandidate} is in the use scope of {@link #myBaseClass}
   */
  public boolean checkInheritance(@NotNull PsiClass inheritorCandidate) {
    if (!inheritorCandidate.isValid() || !myBaseClass.isValid()) return false;
    if (myFileIndex.isInSourceContent(inheritorCandidate.getContainingFile().getVirtualFile())) {
      Pair<PsiClass[], Boolean> value = myClasses.getValue();
      boolean hasGlobalAmbiguities = value.getSecond();
      if (!hasGlobalAmbiguities) {
        return true;
      }

      PsiClass[] classes = value.getFirst();
      GlobalSearchScope scope = inheritorCandidate.getResolveScope();
      List<PsiClass> accessible = ContainerUtil.findAll(classes, base ->
        PsiSearchScopeUtil.isInScope(scope, base) && isAccessibleLight(inheritorCandidate, base));
      if (!hasAmbiguities(accessible)) {
        return accessible.contains(myBaseClass);
      }
    }

    if (inheritorCandidate instanceof PsiCompiledElement && isEnumOrAnnotationInheritor(inheritorCandidate)) {
      return true;
    }

    return inheritorCandidate.isInheritor(myBaseClass, false);
  }

  private boolean isEnumOrAnnotationInheritor(@NotNull PsiClass inheritorCandidate) {
    if (inheritorCandidate.isEnum() && CommonClassNames.JAVA_LANG_ENUM.equals(myBaseClass.getQualifiedName())) {
      return true;
    }
    if (inheritorCandidate.isAnnotationType() && CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(myBaseClass.getQualifiedName())) {
      return true;
    }
    return false;
  }

  private static boolean isAccessibleLight(@NotNull PsiClass inheritorCandidate, @NotNull PsiClass base) {
    PsiModifierList modifierList = base.getModifierList();
    if (modifierList != null && PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      return true; // requires hierarchy checks => resolve
    }

    return JavaResolveUtil.isAccessible(base, base.getContainingClass(), modifierList, inheritorCandidate, null, null);
  }
}

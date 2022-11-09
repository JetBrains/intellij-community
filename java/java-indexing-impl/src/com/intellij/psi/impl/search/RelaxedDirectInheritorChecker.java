// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * To avoid expensive super type resolve, if there's only one suitable class with the required name in the project anyway
 */
public final class RelaxedDirectInheritorChecker {
  private final String myBaseClassName;
  private final PsiClass myBaseClass;
  private final NotNullLazyValue<ClassesAndAmbiguities> myClasses;
  private final ProjectFileIndex myFileIndex;

  public RelaxedDirectInheritorChecker(@NotNull PsiClass baseClass) {
    myBaseClass = baseClass;
    myBaseClassName = Objects.requireNonNull(baseClass.getName());
    myClasses = NotNullLazyValue.volatileLazy(() -> getClassesAndTheirAmbiguities(myBaseClass.getProject(), myBaseClassName));
    myFileIndex = ProjectFileIndex.getInstance(myBaseClass.getProject());
  }

  private record ClassesAndAmbiguities(@NotNull PsiClass @NotNull [] classes, @NotNull PsiFile @NotNull [] containingFiles, boolean isAmbiguous) {
  }

  @NotNull
  private static ClassesAndAmbiguities getClassesAndTheirAmbiguities(@NotNull Project project, @NotNull String classShortName) {
    Map<String, Reference<ClassesAndAmbiguities>> cache = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT));
    ClassesAndAmbiguities result = SoftReference.dereference(cache.get(classShortName));
    if (result == null) {
      PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(classShortName, GlobalSearchScope.allScope(project));
      String ambiguity = null;
      PsiFile[] files = new PsiFile[classes.length];
      for (int i = 0; i < classes.length; i++) {
        PsiClass psiClass = classes[i];
        ambiguity = hasAmbiguitiesSoFar(psiClass, ambiguity);
        files[i] = psiClass.getContainingFile();
      }
      boolean ambiguities = ambiguity == AMBIGUITY_FOUND;
      result = new ClassesAndAmbiguities(classes, files, ambiguities);

      cache.put(classShortName, new SoftReference<>(result));
    }

    return result;
  }

  // because of lack of multiple-value return, we have this monstrosity
  // in the end (after the whole list is traversed with 'a=hasAmbiguitiesSoFar(c,a)' called on each element), one of these cases is returned:
  // - AMBIGUITY_FOUND: if there exist two classes with different FQNs (or at least one local class and at least one with not-null FQN)
  // - LOCAL_CLASS_FOUND: one local class found, all others have same FQN
  // - other: if all classes in the list have the same FQN
  private static String hasAmbiguitiesSoFar(@NotNull PsiClass psiClass, String oldFqn) {
    if (oldFqn == AMBIGUITY_FOUND) return AMBIGUITY_FOUND;
    String qName = Objects.requireNonNullElse(psiClass.getQualifiedName(), LOCAL_CLASS_FOUND);
    return oldFqn == null || oldFqn.equals(qName) && !oldFqn.equals(LOCAL_CLASS_FOUND) ? qName : AMBIGUITY_FOUND;
  }
  private static final String LOCAL_CLASS_FOUND = "?LOCAL_CLASS_FOUND";
  private static final String AMBIGUITY_FOUND = "?AMBIGUITY_FOUND";

  /**
   * This assumes that {@code inheritorCandidate} is in the use scope of {@link #myBaseClass}
   */
  public boolean checkInheritance(@NotNull PsiClass inheritorCandidate) {
    if (!inheritorCandidate.isValid() || !myBaseClass.isValid()) return false;
    PsiFile inheritorCandidateContainingFile = inheritorCandidate.getContainingFile();
    if (myFileIndex.isInSourceContent(inheritorCandidateContainingFile.getVirtualFile())) {
      ClassesAndAmbiguities value = myClasses.getValue();
      boolean hasGlobalAmbiguities = value.isAmbiguous;
      if (!hasGlobalAmbiguities) {
        return true;
      }

      PsiClass[] classes = value.classes;
      PsiFile[] files = value.containingFiles;
      GlobalSearchScope scope = ResolveScopeManager.getInstance(inheritorCandidateContainingFile.getProject()).getResolveScope(inheritorCandidateContainingFile);
      String ambiguity = null;
      boolean hasBaseClass = false;
      for (int i = 0; i < classes.length; i++) {
        PsiClass base = classes[i];
        PsiFile file = files[i];
        if (PsiSearchScopeUtil.isInScope(scope, file) && isAccessibleLight(inheritorCandidate, inheritorCandidateContainingFile, base)) {
          hasBaseClass |= base.equals(myBaseClass);
          ambiguity = hasAmbiguitiesSoFar(base, ambiguity);
        }
      }
      if (ambiguity != AMBIGUITY_FOUND) {
        return hasBaseClass;
      }
    }

    if (inheritorCandidate instanceof PsiCompiledElement && isEnumOrAnnotationInheritor(inheritorCandidate)) {
      return true;
    }

    return inheritorCandidate.isInheritor(myBaseClass, false);
  }

  private boolean isEnumOrAnnotationInheritor(@NotNull PsiClass inheritorCandidate) {
    return inheritorCandidate.isEnum() && CommonClassNames.JAVA_LANG_ENUM.equals(myBaseClass.getQualifiedName()) ||
           inheritorCandidate.isAnnotationType() && CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(myBaseClass.getQualifiedName());
  }

  private static boolean isAccessibleLight(@NotNull PsiClass inheritorCandidate,
                                           @NotNull PsiFile inheritorCandidateContainingFile,
                                           @NotNull PsiClass base) {
    PsiModifierList modifierList = base.getModifierList();
    if (modifierList != null && PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      return true; // requires hierarchy checks => resolve
    }

    return JavaResolveUtil.isAccessible(base, base.getContainingClass(), modifierList, inheritorCandidate, null, null, inheritorCandidateContainingFile);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PsiSuperMethodUtil {
  private PsiSuperMethodUtil() {}

  public static boolean isSuperMethod(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    for (HierarchicalMethodSignature supsig : signature.getSuperSignatures()) {
      PsiMethod supsigme = supsig.getMethod();
      if (superMethod.equals(supsigme) || isSuperMethod(supsigme, superMethod)) return true;
    }

    return false;
  }

  @NotNull
  public static PsiSubstitutor obtainFinalSubstitutor(@NotNull PsiClass superClass,
                                                      @NotNull PsiSubstitutor superSubstitutor,
                                                      @NotNull PsiSubstitutor derivedSubstitutor,
                                                      boolean inRawContext) {
    if (inRawContext) {
      Set<PsiTypeParameter> typeParams = superSubstitutor.getSubstitutionMap().keySet();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(superClass.getProject());
      superSubstitutor = factory.createRawSubstitutor(derivedSubstitutor, typeParams.toArray(PsiTypeParameter.EMPTY_ARRAY));
    }
    Map<PsiTypeParameter, PsiType> map = null;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(superClass)) {
      PsiType type = superSubstitutor.substitute(typeParameter);
      final PsiType t = derivedSubstitutor.substitute(type);
      if (map == null) {
        map = new THashMap<>();
      }
      map.put(typeParameter, t);
    }

    return map == null ? PsiSubstitutor.EMPTY : JavaPsiFacade.getElementFactory(superClass.getProject()).createSubstitutor(map);
  }

  @NotNull
  public static Map<MethodSignature, Set<PsiMethod>> collectOverrideEquivalents(@NotNull PsiClass aClass) {
    final Map<MethodSignature, Set<PsiMethod>> overrideEquivalent =
      new THashMap<>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
    final GlobalSearchScope resolveScope = aClass.getResolveScope();
    PsiClass[] supers = aClass.getSupers();
    for (int i = 0; i < supers.length; i++) {
      PsiClass superClass = supers[i];
      boolean subType = false;
      for (int j = 0; j < supers.length; j++) {
        if (j == i) continue;
        subType |= supers[j].isInheritor(supers[i], true);
      }
      if (subType) continue;
      final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      for (HierarchicalMethodSignature hms : superClass.getVisibleSignatures()) {
        PsiMethod method = hms.getMethod();
        if (MethodSignatureUtil.findMethodBySignature(aClass, method.getSignature(superClassSubstitutor), false) != null) continue;
        PsiClass methodClass = method.getContainingClass();
        if (methodClass == null) continue;
        final PsiClass containingClass = correctClassByScope(methodClass, resolveScope);
        if (containingClass == null) continue;
        method = containingClass.findMethodBySignature(method, false);
        if (method == null) continue;
        final PsiSubstitutor containingClassSubstitutor = TypeConversionUtil.getClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY);
        if (containingClassSubstitutor == null) continue;
        final PsiSubstitutor finalSubstitutor =
          obtainFinalSubstitutor(containingClass, containingClassSubstitutor, hms.getSubstitutor(), false);
        final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, finalSubstitutor, false);
        Set<PsiMethod> methods = overrideEquivalent.computeIfAbsent(signature, __ -> new LinkedHashSet<>());
        methods.add(method);
      }
    }
    return overrideEquivalent;
  }

  @Nullable
  public static PsiClass correctClassByScope(@NotNull PsiClass psiClass, @NotNull GlobalSearchScope resolveScope) {
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return psiClass;
    }

    PsiFile file = psiClass.getContainingFile();
    if (file == null || !file.getViewProvider().isPhysical()) {
      return psiClass;
    }

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      return psiClass;
    }

    final FileIndexFacade index = FileIndexFacade.getInstance(file.getProject());
    if (!index.isInSource(vFile) && !index.isInLibrarySource(vFile) && !index.isInLibraryClasses(vFile)) {
      return psiClass;
    }

    return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(qualifiedName, resolveScope);
  }

}

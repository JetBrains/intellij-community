// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PsiSuperMethodUtil {
  private PsiSuperMethodUtil() {}

  public static boolean isSuperMethod(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
      PsiMethod supsigme = superSignature.getMethod();
      if (superMethod.equals(supsigme) || isSuperMethod(supsigme, superMethod)) return true;
    }

    return false;
  }

  public static @NotNull PsiSubstitutor obtainFinalSubstitutor(@NotNull PsiClass superClass,
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
        map = new HashMap<>();
      }
      map.put(typeParameter, t);
    }

    return map == null ? PsiSubstitutor.EMPTY : JavaPsiFacade.getElementFactory(superClass.getProject()).createSubstitutor(map);
  }

  public static @NotNull Map<MethodSignature, Set<PsiMethod>> collectOverrideEquivalents(@NotNull PsiClass aClass) {
    final Map<MethodSignature, Set<PsiMethod>> overrideEquivalent = MethodSignatureUtil.createErasedMethodSignatureMap();
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

  /**
   * Maps the given class to the class which is located in the specified resolve scope.
   * <p/>
   * For the multi-module projects which use different jdks or libraries,
   * it's important to map e.g. super class hierarchy to the current jdk.
   * <p>Example:</p>
   * Suppose there is an abstract reader in a module with jdk 1.6 which inherits {@link Closeable} (no super interfaces!). 
   * In another module with jdk 1.7+ an inheritor of this reader should implement {@link AutoCloseable} though.
   * 
   * @param psiClass       a class to remap
   * @param resolveScope   scope where class should be found
   * @return               remapped class or same, if no other candidates were found
   */
  public static @Nullable PsiClass correctClassByScope(@NotNull PsiClass psiClass, @NotNull GlobalSearchScope resolveScope) {
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
    if (!index.isInSource(vFile) && !index.isInLibrary(vFile)) {
      return psiClass;
    }

    PsiClass aClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(qualifiedName, resolveScope);
    VirtualFile mappedVFile = PsiUtilCore.getVirtualFile(aClass);
    if (mappedVFile != null) {
      Module module = index.getModuleForFile(vFile);
      if (module != null && module == index.getModuleForFile(mappedVFile)) {
        return psiClass;
      }
    }
    return aClass;
  }

}

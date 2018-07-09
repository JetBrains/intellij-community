// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author peter
 */
class TypeCorrector extends PsiTypeMapper {
  private final Map<PsiClassType, PsiClassType> myResultMap = ContainerUtil.newIdentityHashMap();
  private final GlobalSearchScope myResolveScope;

  TypeCorrector(GlobalSearchScope resolveScope) {
    myResolveScope = resolveScope;
  }

  @Override
  public PsiType visitType(PsiType type) {
    if (LambdaUtil.notInferredType(type)) {
      return type;
    }
    return super.visitType(type);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends PsiType> T correctType(@NotNull T type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      if (classType.getParameterCount() == 0) {
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && classResolveResult.getSubstitutor() == PsiSubstitutor.EMPTY) {
          final PsiClass mappedClass = PsiSuperMethodUtil.correctClassByScope(psiClass, myResolveScope);
          if (mappedClass == null || mappedClass == psiClass) return (T) classType;
        }
      }
    }

    return (T)type.accept(this);
  }

  @Override
  public PsiType visitClassType(PsiClassType classType) {
    if (classType instanceof PsiCorrectedClassType) {
      return myResolveScope.equals(classType.getResolveScope()) ? classType :
             visitClassType(((PsiCorrectedClassType)classType).myDelegate);
    }
    
    PsiClassType alreadyComputed = myResultMap.get(classType);
    if (alreadyComputed != null) {
      return alreadyComputed;
    }

    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (psiClass == null) return classType;

    PsiUtilCore.ensureValid(psiClass);

    final PsiClass mappedClass = PsiSuperMethodUtil.correctClassByScope(psiClass, myResolveScope);
    if (mappedClass == null) return classType;

    PsiClassType mappedType = new PsiCorrectedClassType(classType.getLanguageLevel(),
                                                        classType,
                                                        new CorrectedResolveResult(psiClass, mappedClass, substitutor, classResolveResult));
    myResultMap.put(classType, mappedType);
    return mappedType;
  }

  @NotNull
  private PsiSubstitutor mapSubstitutor(PsiClass originalClass, PsiClass mappedClass, PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = mappedClass.getTypeParameters();
    PsiTypeParameter[] originalTypeParameters = originalClass.getTypeParameters();
    if (typeParameters.length != originalTypeParameters.length) {
      if (originalTypeParameters.length == 0) {
        return JavaPsiFacade.getElementFactory(mappedClass.getProject()).createRawSubstitutor(mappedClass);
      }
      return substitutor;
    }

    Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();

    PsiSubstitutor mappedSubstitutor = PsiSubstitutor.EMPTY;
    for (int i = 0; i < originalTypeParameters.length; i++) {
      if (!substitutionMap.containsKey(originalTypeParameters[i])) continue;

      PsiType originalSubstitute = substitutor.substitute(originalTypeParameters[i]);
      if (originalSubstitute != null) {
        PsiType substitute = mapType(originalSubstitute);
        if (substitute == null) return substitutor;

        mappedSubstitutor = mappedSubstitutor.put(typeParameters[i], substitute);
      }
      else {
        mappedSubstitutor = mappedSubstitutor.put(typeParameters[i], null);
      }
    }

    if (mappedClass.hasModifierProperty(PsiModifier.STATIC)) {
      return mappedSubstitutor;
    }
    PsiClass mappedContaining = mappedClass.getContainingClass();
    PsiClass originalContaining = originalClass.getContainingClass();
    //noinspection DoubleNegation
    if ((mappedContaining != null) != (originalContaining != null)) {
      return substitutor;
    }

    if (mappedContaining != null) {
      return mappedSubstitutor.putAll(mapSubstitutor(originalContaining, mappedContaining, substitutor));
    }

    return mappedSubstitutor;
  }

  private class PsiCorrectedClassType extends PsiClassType.Stub {
    private final PsiClassType myDelegate;
    private final CorrectedResolveResult myResolveResult;

    private PsiCorrectedClassType(@NotNull LanguageLevel languageLevel,
                                  PsiClassType delegate,
                                  CorrectedResolveResult resolveResult) {
      super(languageLevel, delegate.getAnnotationProvider());
      if (delegate instanceof PsiCorrectedClassType) {
        throw new IllegalArgumentException();
      }
      myDelegate = delegate;
      myResolveResult = resolveResult;
    }

    @NotNull
    @Override
    public PsiClass resolve() {
      return myResolveResult.myMappedClass;
    }

    @Override
    public String getClassName() {
      return myDelegate.getClassName();
    }

    @NotNull
    @Override
    public PsiType[] getParameters() {
      return ContainerUtil.map2Array(myDelegate.getParameters(), PsiType.class, type -> {
        if (type == null) {
          LOG.error(myDelegate + " of " + myDelegate.getClass() + "; substitutor=" + myDelegate.resolveGenerics().getSubstitutor());
          return null;
        }
        return mapType(type);
      });
    }

    @Override
    public int getParameterCount() {
      return myDelegate.getParameters().length;
    }

    @NotNull
    @Override
    public ClassResolveResult resolveGenerics() {
      return myResolveResult;
    }

    @NotNull
    @Override
    public PsiClassType rawType() {
      PsiClass psiClass = resolve();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      return factory.createType(psiClass, factory.createRawSubstitutor(psiClass));
    }

    @NotNull
    @Override
    public GlobalSearchScope getResolveScope() {
      return myResolveScope;
    }

    @NotNull
    @Override
    public LanguageLevel getLanguageLevel() {
      return myLanguageLevel;
    }

    @NotNull
    @Override
    public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
      return new PsiCorrectedClassType(languageLevel, myDelegate, myResolveResult);
    }

    @NotNull
    @Override
    public String getPresentableText(boolean annotated) {
      return myDelegate.getPresentableText(annotated);
    }

    @NotNull
    @Override
    public String getCanonicalText(boolean annotated) {
      return myDelegate.getCanonicalText(annotated);
    }

    @NotNull
    @Override
    public String getInternalCanonicalText() {
      return myDelegate.getInternalCanonicalText();
    }

    @Override
    public boolean isValid() {
      return myDelegate.isValid() && myResolveResult.myMappedClass.isValid() && myResolveResult.mySubstitutor.isValid();
    }

    @Override
    public boolean equalsToText(@NotNull @NonNls String text) {
      return myDelegate.equalsToText(text);
    }
  }

  private class CorrectedResolveResult implements PsiClassType.ClassResolveResult {
    private final PsiClass myPsiClass;
    private final PsiClass myMappedClass;
    private final PsiSubstitutor mySubstitutor;
    private final PsiClassType.ClassResolveResult myClassResolveResult;
    private volatile PsiSubstitutor myLazySubstitutor;

    public CorrectedResolveResult(PsiClass psiClass,
                                  PsiClass mappedClass,
                                  PsiSubstitutor substitutor,
                                  PsiClassType.ClassResolveResult classResolveResult) {
      myPsiClass = psiClass;
      myMappedClass = mappedClass;
      mySubstitutor = substitutor;
      myClassResolveResult = classResolveResult;
    }

    @NotNull
    @Override
    public PsiSubstitutor getSubstitutor() {
      PsiSubstitutor result = myLazySubstitutor;
      if (result == null) {
        myLazySubstitutor = result = mapSubstitutor(myPsiClass, myMappedClass, mySubstitutor);
      }
      return result;
    }

    @Override
    public PsiClass getElement() {
      return myMappedClass;
    }

    @Override
    public boolean isPackagePrefixPackageReference() {
      return myClassResolveResult.isPackagePrefixPackageReference();
    }

    @Override
    public boolean isAccessible() {
      return myClassResolveResult.isAccessible();
    }

    @Override
    public boolean isStaticsScopeCorrect() {
      return myClassResolveResult.isStaticsScopeCorrect();
    }

    @Override
    public PsiElement getCurrentFileResolveScope() {
      return myClassResolveResult.getCurrentFileResolveScope();
    }

    @Override
    public boolean isValidResult() {
      return myClassResolveResult.isValidResult();
    }
  }
}

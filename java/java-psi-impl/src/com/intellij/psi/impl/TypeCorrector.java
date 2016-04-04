/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
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
    if (type instanceof PsiLambdaParameterType || type instanceof PsiLambdaExpressionType || type instanceof PsiMethodReferenceType) {
      return type;
    }
    return super.visitType(type);
  }

  @Nullable
  public <T extends PsiType> T correctType(@NotNull T type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      if (classType.getParameterCount() == 0) {
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && classResolveResult.getSubstitutor() == PsiSubstitutor.EMPTY) {
          final PsiClass mappedClass = mapClass(psiClass);
          if (mappedClass == null || mappedClass == psiClass) return (T) classType;
        }
      }
    }

    return (T)type.accept(this);
  }

  @Override
  public PsiType visitClassType(final PsiClassType classType) {
    PsiClassType alreadyComputed = myResultMap.get(classType);
    if (alreadyComputed != null) {
      return alreadyComputed;
    }

    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (psiClass == null) return classType;

    PsiUtilCore.ensureValid(psiClass);

    final PsiClass mappedClass = mapClass(psiClass);
    if (mappedClass == null) return classType;

    PsiClassType mappedType = new PsiCorrectedClassType(classType.getLanguageLevel(),
                                                        classType,
                                                        new CorrectedResolveResult(psiClass, mappedClass, substitutor, classResolveResult));
    myResultMap.put(classType, mappedType);
    return mappedType;
  }

  @Nullable
  private PsiClass mapClass(@NotNull PsiClass psiClass) {
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

    return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(qualifiedName, myResolveScope);
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

    public PsiCorrectedClassType(LanguageLevel languageLevel,
                                 PsiClassType delegate,
                                 CorrectedResolveResult resolveResult) {
      super(languageLevel, delegate.getAnnotationProvider());
      myDelegate = delegate;
      myResolveResult = resolveResult;
    }

    @NotNull
    @Override
    public String getCanonicalText(boolean annotated) {
      return myDelegate.getCanonicalText();
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
      return ContainerUtil.map2Array(myDelegate.getParameters(), PsiType.class, new Function<PsiType, PsiType>() {
        @Override
        public PsiType fun(PsiType type) {
          if (type == null) {
            LOG.error(myDelegate + " of " + myDelegate.getClass() + "; substitutor=" + myDelegate.resolveGenerics().getSubstitutor());
            return null;
          }
          return mapType(type);
        }
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
    public String getPresentableText() {
      return myDelegate.getPresentableText();
    }

    @NotNull
    @Override
    public String getInternalCanonicalText() {
      return myDelegate.getInternalCanonicalText();
    }

    @Override
    public boolean isValid() {
      return myDelegate.isValid() && resolve().isValid();
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

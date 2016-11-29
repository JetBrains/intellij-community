/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightClassReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  private final String myClassName;
  private final PsiElement myContext;
  @NotNull private final GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;
  private final PsiSubstitutor mySubstitutor;

  private LightClassReference(@NotNull PsiManager manager,
                              @NotNull @NonNls String text,
                              @Nullable @NonNls String className,
                              @Nullable PsiSubstitutor substitutor,
                              @NotNull GlobalSearchScope resolveScope,
                              @Nullable PsiElement context,
                              @Nullable PsiClass refClass) {
    super(manager, text);
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = context;
    myRefClass = refClass;
    mySubstitutor = substitutor;
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull @NonNls String className, @NotNull GlobalSearchScope resolveScope) {
    this(manager, text, className, null, resolveScope, null, null);
  }

  public LightClassReference(@NotNull PsiManager manager,
                             @NotNull @NonNls String text,
                             @NotNull @NonNls String className,
                             PsiSubstitutor substitutor,
                             @NotNull PsiElement context) {
    this(manager, text, className, substitutor, context.getResolveScope(), context, null);
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull PsiClass refClass) {
    this(manager, text, refClass, null);
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull PsiClass refClass, PsiSubstitutor substitutor) {
    this(manager, text, null, substitutor, refClass.getResolveScope(), null, refClass);
  }

  @Override
  public PsiElement resolve() {
    if (myClassName != null) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      if (myContext != null) {
        return facade.getResolveHelper().resolveReferencedClass(myClassName, myContext);
      }
      else {
        return facade.findClass(myClassName, myResolveScope);
      }
    }
    else {
      return myRefClass;
    }
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    final PsiElement resolved = resolve();
    if (resolved == null) {
      return JavaResolveResult.EMPTY;
    }
    PsiSubstitutor substitutor = mySubstitutor;
    if (substitutor == null) {
      if (resolved instanceof PsiClass) {
        substitutor = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createRawSubstitutor((PsiClass) resolved);
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return new CandidateInfo(resolved, substitutor);
  }

  @Override
  public String getQualifiedName() {
    if (myClassName != null) {
      if (myContext != null) {
        PsiClass psiClass = (PsiClass)resolve();
        if (psiClass != null) {
          return psiClass.getQualifiedName();
        }
      }
      return myClassName;
    }
    return myRefClass.getQualifiedName();
  }

  @Override
  public String getReferenceName() {
    if (myClassName != null){
      return PsiNameHelper.getShortClassName(myClassName);
    }
    else{
      if (myRefClass instanceof PsiAnonymousClass){
        return ((PsiAnonymousClass)myRefClass).getBaseClassReference().getReferenceName();
      }
      else{
        return myRefClass.getName();
      }
    }
  }

  @Override
  public PsiElement copy() {
    if (myClassName != null) {
      if (myContext != null) {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myContext);
      }
      else{
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myResolveScope, null, null);
      }
    }
    else {
      return new LightClassReference(myManager, myText, myRefClass, mySubstitutor);
    }
  }

  @Override
  public boolean isValid() {
    return myRefClass == null || myRefClass.isValid();
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }
}

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
package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightClassReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myText;
  private final String myClassName;
  private final PsiElement myContext;
  private final GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;
  private PsiSubstitutor mySubstitutor;

  private LightReferenceParameterList myParameterList;

  private LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, PsiSubstitutor substitutor, GlobalSearchScope resolveScope) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myText = text;
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = null;
    myRefClass = null;
    mySubstitutor = substitutor;
  }

  public LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, GlobalSearchScope resolveScope) {
    this (manager, text, className, null, resolveScope);
  }

  public LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, PsiSubstitutor substitutor, PsiElement context) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myText = text;
    myClassName = className;
    mySubstitutor = substitutor;
    myContext = context;

    myResolveScope = null;
    myRefClass = null;
  }

  public LightClassReference(PsiManager manager, @NonNls String text, PsiClass refClass) {
    this(manager, text, refClass, null);
  }

  public LightClassReference(PsiManager manager, @NonNls String text, PsiClass refClass, PsiSubstitutor substitutor) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myText = text;
    myRefClass = refClass;

    myResolveScope = null;
    myClassName = null;
    myContext = null;
    mySubstitutor = substitutor;
  }

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

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public void processVariants(PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    if (myParameterList == null) {
      myParameterList = new LightReferenceParameterList(myManager, PsiTypeElement.EMPTY_ARRAY);
    }
    return myParameterList;
  }

  public String getQualifiedName() {
    if (myClassName != null) {
      PsiClass psiClass = (PsiClass)resolve();

      if (psiClass != null) {
        return psiClass.getQualifiedName();
      }
      else {
        return myClassName;
      }
    }
    else {
      return myRefClass.getQualifiedName();
    }
  }

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

  public String getText() {
    return myText;
  }

  public PsiReference getReference() {
    return this;
  }

  public String getCanonicalText() {
    String name = getQualifiedName();
    if (name == null) return null;
    PsiType[] types = getTypeParameters();
    if (types.length == 0) return name;

    StringBuffer buf = new StringBuffer();
    buf.append(name);
    buf.append('<');
    for (int i = 0; i < types.length; i++) {
      if (i > 0) buf.append(',');
      buf.append(types[i].getCanonicalText());
    }
    buf.append('>');

    return buf.toString();
  }

  public PsiElement copy() {
    if (myClassName != null) {
      if (myContext != null) {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myContext);
      }
      else{
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myResolveScope);
      }
    }
    else {
      return new LightClassReference(myManager, myText, myRefClass, mySubstitutor);
    }
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "LightClassReference:" + myText;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  public boolean isSoft(){
    return false;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  public boolean isValid() {
    return myRefClass == null || myRefClass.isValid();
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiElement getQualifier() {
    return null;
  }

  public boolean isQualified() {
    return false;
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LightPackageReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myPackageName;
  private final PsiPackage myRefPackage;

  public LightPackageReference(PsiManager manager, PsiPackage refPackage) {
    super(manager, JavaLanguage.INSTANCE);
    myPackageName = null;
    myRefPackage = refPackage;
  }

  public LightPackageReference(PsiManager manager, String packageName) {
    super(manager, JavaLanguage.INSTANCE);
    myPackageName = packageName;
    myRefPackage = null;
  }

  @Override
  public PsiElement resolve(){
    if (myPackageName != null){
      return JavaPsiFacade.getInstance(myManager.getProject()).findPackage(myPackageName);
    }
    else {
      return myRefPackage;
    }
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    PsiElement resolve = resolve();
    return resolve == null ? JavaResolveResult.EMPTY : new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public String getText(){
    if (myPackageName != null){
      return myPackageName;
    }
    else {
      return myRefPackage.getQualifiedName();
    }
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  @NotNull
  public String getCanonicalText(){
    return getText();
  }

  @Override
  public PsiElement copy(){
    if (myPackageName != null){
      return new LightPackageReference(myManager, myPackageName);
    }
    else{
      return new LightPackageReference(myManager, myRefPackage);
    }
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiJavaCodeReferenceElement:" + getText();
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiPackage)) return false;
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public boolean isSoft(){
    return false;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return getText();
  }

  @Override
  public String getReferenceName() {
    if (myPackageName != null){
      return PsiNameHelper.getShortClassName(myPackageName);
    }
    else {
      return myRefPackage.getName();
    }
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public boolean isValid() {
    return myRefPackage == null || myRefPackage.isValid();
  }

  @Override
  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }

  @Override
  public boolean isQualified() {
    return false;
  }
}

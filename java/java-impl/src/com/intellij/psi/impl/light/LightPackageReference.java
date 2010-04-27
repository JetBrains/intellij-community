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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LightPackageReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myPackageName;
  private final PsiPackage myRefPackage;

  public LightPackageReference(PsiManager manager, PsiPackage refPackage) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myPackageName = null;
    myRefPackage = refPackage;
  }

  public LightPackageReference(PsiManager manager, String packageName) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myPackageName = packageName;
    myRefPackage = null;
  }

  public PsiElement resolve(){
    if (myPackageName != null){
      return JavaPsiFacade.getInstance(myManager.getProject()).findPackage(myPackageName);
    }
    else {
      return myRefPackage;
    }
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    return new CandidateInfo(resolve(), PsiSubstitutor.EMPTY);
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public String getText(){
    if (myPackageName != null){
      return myPackageName;
    }
    else {
      return myRefPackage.getQualifiedName();
    }
  }

  public PsiReference getReference() {
    return this;
  }

  @NotNull
  public String getCanonicalText(){
    return getText();
  }

  public PsiElement copy(){
    if (myPackageName != null){
      return new LightPackageReference(myManager, myPackageName);
    }
    else{
      return new LightPackageReference(myManager, myRefPackage);
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

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiPackage)) return false;
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  public boolean isSoft(){
    return false;
  }

  public void processVariants(PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public String getQualifiedName() {
    return getText();
  }

  public String getReferenceName() {
    if (myPackageName != null){
      return PsiNameHelper.getShortClassName(myPackageName);
    }
    else {
      return myRefPackage.getName();
    }
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  public boolean isValid() {
    return myRefPackage == null || myRefPackage.isValid();
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

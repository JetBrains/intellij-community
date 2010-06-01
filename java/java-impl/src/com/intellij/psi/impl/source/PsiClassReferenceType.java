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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class PsiClassReferenceType extends PsiClassType {
  private final PsiJavaCodeReferenceElement myReference;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel languageLevel) {
    super(languageLevel, extractAnnosFromReference(reference));
    myReference = reference;
  }

  private static PsiAnnotation[] extractAnnosFromReference(PsiJavaCodeReferenceElement reference) {
    List<PsiAnnotation> result = null;
    for(PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof PsiAnnotation) {
        if (result == null) result = new SmartList<PsiAnnotation>();
        result.add((PsiAnnotation)child);
      }
    }

    if (result == null) return PsiAnnotation.EMPTY_ARRAY;
    return result.toArray(new PsiAnnotation[result.size()]);
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel languageLevel, PsiAnnotation[] annotations) {
    super(languageLevel,annotations);
    myReference = reference;
  }

  public boolean isValid() {
    return myReference.isValid();
  }

  public boolean equalsToText(String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myReference.getResolveScope();
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(myReference);
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    return new PsiClassReferenceType(myReference,languageLevel,getAnnotations());
  }

  public PsiClass resolve() {
    return resolveGenerics().getElement();
  }

  private static class DelegatingClassResolveResult implements ClassResolveResult {
    private final JavaResolveResult myDelegate;

    private DelegatingClassResolveResult(JavaResolveResult delegate) {
      myDelegate = delegate;
    }

    public PsiSubstitutor getSubstitutor() {
      return myDelegate.getSubstitutor();
    }

    public boolean isValidResult() {
      return myDelegate.isValidResult();
    }

    public boolean isAccessible() {
      return myDelegate.isAccessible();
    }

    public boolean isStaticsScopeCorrect() {
      return myDelegate.isStaticsScopeCorrect();
    }

    public PsiElement getCurrentFileResolveScope() {
      return myDelegate.getCurrentFileResolveScope();
    }

    public boolean isPackagePrefixPackageReference() {
      return myDelegate.isPackagePrefixPackageReference();
    }

    public PsiClass getElement() {
      final PsiElement element = myDelegate.getElement();
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }

  @NotNull
  public ClassResolveResult resolveGenerics() {
    final JavaResolveResult result = myReference.advancedResolve(false);
    return new DelegatingClassResolveResult(result);
  }

  @NotNull
  public PsiClassType rawType() {
    PsiElement resolved = myReference.resolve();
    if (resolved instanceof PsiClass) {
      PsiClass aClass = (PsiClass)resolved;
      if (!PsiUtil.typeParametersIterable(aClass).iterator().hasNext()) return this;
      PsiManager manager = myReference.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      final PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor(aClass);
      return factory.createType(aClass, rawSubstitutor, getLanguageLevel(), getAnnotations());
    }
    String qualifiedName = myReference.getQualifiedName();
    String name = myReference.getReferenceName();
    if (name==null) name="";
    LightClassReference reference = new LightClassReference(myReference.getManager(), name, qualifiedName, myReference.getResolveScope());
    return new PsiClassReferenceType(reference, null,getAnnotations());
  }

  public String getClassName() {
    return myReference.getReferenceName();
  }

  @NotNull
  public PsiType[] getParameters() {
    return myReference.getTypeParameters();
  }

  public PsiClassType createImmediateCopy() {
    final ClassResolveResult resolveResult = resolveGenerics();
    if (resolveResult.getElement() == null) return this;
    return new PsiImmediateClassType(resolveResult.getElement(), resolveResult.getSubstitutor());
  }

  public String getPresentableText() {
    return getAnnotationsTextPrefix() + PsiNameHelper.getPresentableText(myReference);
  }

  public String getCanonicalText() {
    return myReference.getCanonicalText();
  }

  public String getInternalCanonicalText() {
    return getAnnotationsTextPrefix() + getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getReference() {
    return myReference;
  }
}

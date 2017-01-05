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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class PsiClassReferenceType extends PsiClassType.Stub {
  private final Computable<PsiJavaCodeReferenceElement> myReference;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
    this(reference, level, collectAnnotations(reference));
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull PsiAnnotation[] annotations) {
    super(level, annotations);
    myReference = new Computable.PredefinedValueComputable<PsiJavaCodeReferenceElement>(reference);
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    this(new Computable.PredefinedValueComputable<PsiJavaCodeReferenceElement>(reference), level, provider);
  }

  public PsiClassReferenceType(@NotNull Computable<PsiJavaCodeReferenceElement> reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    super(level, provider);
    myReference = reference;
  }

  private static PsiAnnotation[] collectAnnotations(PsiJavaCodeReferenceElement reference) {
    List<PsiAnnotation> result = null;
    for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiAnnotation) {
        if (result == null) result = new SmartList<PsiAnnotation>();
        result.add((PsiAnnotation)child);
      }
    }
    return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @Override
  public boolean isValid() {
    PsiJavaCodeReferenceElement reference = myReference.compute();
    return reference != null && reference.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return getReference().getResolveScope();
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(getReference());
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    return new PsiClassReferenceType(getReference(), languageLevel, getAnnotationProvider());
  }

  @Override
  public PsiClass resolve() {
    return resolveGenerics().getElement();
  }

  private static class DelegatingClassResolveResult implements PsiClassType.ClassResolveResult {
    private final JavaResolveResult myDelegate;

    private DelegatingClassResolveResult(@NotNull JavaResolveResult delegate) {
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public PsiSubstitutor getSubstitutor() {
      return myDelegate.getSubstitutor();
    }

    @Override
    public boolean isValidResult() {
      return myDelegate.isValidResult();
    }

    @Override
    public boolean isAccessible() {
      return myDelegate.isAccessible();
    }

    @Override
    public boolean isStaticsScopeCorrect() {
      return myDelegate.isStaticsScopeCorrect();
    }

    @Override
    public PsiElement getCurrentFileResolveScope() {
      return myDelegate.getCurrentFileResolveScope();
    }

    @Override
    public boolean isPackagePrefixPackageReference() {
      return myDelegate.isPackagePrefixPackageReference();
    }

    @Override
    public PsiClass getElement() {
      final PsiElement element = myDelegate.getElement();
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }

  @Override
  @NotNull
  public ClassResolveResult resolveGenerics() {
    PsiJavaCodeReferenceElement reference = getReference();
    PsiUtilCore.ensureValid(reference);
    final JavaResolveResult result = reference.advancedResolve(false);
    return result.getElement() == null ? ClassResolveResult.EMPTY : new DelegatingClassResolveResult(result);
  }

  @Override
  @NotNull
  public PsiClassType rawType() {
    PsiJavaCodeReferenceElement reference = getReference();
    PsiElement resolved = reference.resolve();
    if (resolved instanceof PsiClass) {
      PsiClass aClass = (PsiClass)resolved;
      if (!PsiUtil.typeParametersIterable(aClass).iterator().hasNext()) return this;
      PsiManager manager = reference.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      final PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor(aClass);
      return new PsiImmediateClassType(aClass, rawSubstitutor, getLanguageLevel(), getAnnotationProvider());
    }
    String qualifiedName = reference.getQualifiedName();
    String name = reference.getReferenceName();
    if (name == null) name = "";
    LightClassReference lightReference = new LightClassReference(reference.getManager(), name, qualifiedName, reference.getResolveScope());
    return new PsiClassReferenceType(lightReference, null, getAnnotationProvider());
  }

  @Override
  public String getClassName() {
    return getReference().getReferenceName();
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return getReference().getTypeParameters();
  }

  @NotNull
  public PsiClassType createImmediateCopy() {
    ClassResolveResult resolveResult = resolveGenerics();
    PsiClass element = resolveResult.getElement();
    return element == null ? this : new PsiImmediateClassType(element, resolveResult.getSubstitutor());
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    String presentableText = PsiNameHelper.getPresentableText(getReference());

    PsiAnnotation[] annotations = annotated ? getAnnotations() : PsiAnnotation.EMPTY_ARRAY;
    if (annotations.length == 0) return presentableText;

    StringBuilder sb = new StringBuilder();
    PsiNameHelper.appendAnnotations(sb, annotations, false);
    sb.append(presentableText);
    return sb.toString();
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText(true);
  }

  private String getText(boolean annotated) {
    PsiJavaCodeReferenceElement reference = getReference();
    if (reference instanceof PsiAnnotatedJavaCodeReferenceElement) {
      PsiAnnotatedJavaCodeReferenceElement ref = (PsiAnnotatedJavaCodeReferenceElement)reference;
      PsiAnnotation[] annotations = annotated ? getAnnotations() : PsiAnnotation.EMPTY_ARRAY;
      return ref.getCanonicalText(annotated, annotations.length == 0 ? null : annotations);
    }
    return reference.getCanonicalText();
  }

  @NotNull
  public PsiJavaCodeReferenceElement getReference() {
    return ObjectUtils.assertNotNull(myReference.compute());
  }
}
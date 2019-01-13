/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author max
 */
public class PsiClassReferenceType extends PsiClassType.Stub {
  private final Computable<? extends PsiJavaCodeReferenceElement> myReference;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
    this(reference, level, collectAnnotations(reference));
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull PsiAnnotation[] annotations) {
    super(level, annotations);
    myReference = new Computable.PredefinedValueComputable<>(reference);
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    this(new Computable.PredefinedValueComputable<>(reference), level, provider);
  }

  public PsiClassReferenceType(@NotNull Computable<? extends PsiJavaCodeReferenceElement> reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    super(level, provider);
    myReference = reference;
  }

  @NotNull
  private static PsiAnnotation[] collectAnnotations(PsiJavaCodeReferenceElement reference) {
    List<PsiAnnotation> result = null;
    for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiAnnotation) {
        if (result == null) result = new SmartList<>();
        result.add((PsiAnnotation)child);
      }
    }
    return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public boolean isValid() {
    PsiJavaCodeReferenceElement reference = myReference.compute();
    if (reference != null && reference.isValid()) {
      for (PsiAnnotation annotation : getAnnotations(false)) {
        if (!annotation.isValid()) return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    PsiJavaCodeReferenceElement reference = getReference();
    String name = reference.getReferenceName();
    return (name == null || text.contains(name)) && Comparing.equal(text, getCanonicalText());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return getReference().getResolveScope();
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return getAnnotations(true);
  }

  private PsiAnnotation[] getAnnotations(boolean merge) {
    PsiAnnotation[] annotations = super.getAnnotations();

    if (merge) {
      PsiJavaCodeReferenceElement reference = myReference.compute();
      if (reference != null && reference.isValid() && reference.isQualified()) {
        PsiAnnotation[] embedded = collectAnnotations(reference);
        if (annotations.length > 0 && embedded.length > 0) {
          LinkedHashSet<PsiAnnotation> set = ContainerUtil.newLinkedHashSet();
          ContainerUtil.addAll(set, annotations);
          ContainerUtil.addAll(set, embedded);
          annotations = set.toArray(PsiAnnotation.EMPTY_ARRAY);
        }
        else {
          annotations = ArrayUtil.mergeArrays(annotations, embedded);
        }
      }
    }

    return annotations;
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
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
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
  @Override
  public String getPresentableText(boolean annotated) {
    String presentableText = PsiNameHelper.getPresentableText(getReference());

    PsiAnnotation[] annotations = annotated ? getAnnotations(false) : PsiAnnotation.EMPTY_ARRAY;
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
      PsiAnnotation[] annotations = annotated ? getAnnotations(false) : PsiAnnotation.EMPTY_ARRAY;
      return ref.getCanonicalText(annotated, annotations.length == 0 ? null : annotations);
    }
    return reference.getCanonicalText();
  }

  @NotNull
  public PsiJavaCodeReferenceElement getReference() {
    return ObjectUtils.assertNotNull(myReference.compute());
  }
}
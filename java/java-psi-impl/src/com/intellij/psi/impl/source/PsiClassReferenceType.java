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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class PsiClassReferenceType extends PsiClassType.Stub {
  private final PsiJavaCodeReferenceElement myReference;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
    this(reference, level, collectAnnotations(reference));
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull PsiAnnotation[] annotations) {
    super(level, annotations);
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
    return myReference.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myReference.getResolveScope();
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(myReference);
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    return new PsiClassReferenceType(myReference, languageLevel, getAnnotations());
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
    PsiUtilCore.ensureValid(myReference);
    final JavaResolveResult result = myReference.advancedResolve(false);
    return result.getElement() == null ? ClassResolveResult.EMPTY : new DelegatingClassResolveResult(result);
  }

  @Override
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
    if (name == null) name = "";
    LightClassReference reference = new LightClassReference(myReference.getManager(), name, qualifiedName, myReference.getResolveScope());
    return new PsiClassReferenceType(reference, null, getAnnotations());
  }

  @Override
  public String getClassName() {
    return myReference.getReferenceName();
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return myReference.getTypeParameters();
  }

  @NotNull
  public PsiClassType createImmediateCopy() {
    ClassResolveResult resolveResult = resolveGenerics();
    PsiClass element = resolveResult.getElement();
    return element == null ? this : new PsiImmediateClassType(element, resolveResult.getSubstitutor());
  }

  @NotNull
  @Override
  public String getPresentableText() {
    String presentableText = PsiNameHelper.getPresentableText(myReference);
    PsiAnnotation[] annotations = getAnnotations();
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
    return getText(true);
  }

  private String getText(boolean annotated) {
    if (myReference instanceof PsiJavaCodeReferenceElementImpl) {
      PsiAnnotation[] annotations = getAnnotations();
      if (!annotated || annotations.length == 0) annotations = null;
      PsiJavaCodeReferenceElementImpl impl = (PsiJavaCodeReferenceElementImpl)myReference;
      return impl.getCanonicalText(annotated, annotations, impl.getContainingFile());
    }
    return myReference.getCanonicalText();
  }

  @NotNull
  public PsiJavaCodeReferenceElement getReference() {
    return myReference;
  }
}

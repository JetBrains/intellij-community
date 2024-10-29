// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightClassTypeReference;
import com.intellij.psi.infos.PatternCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

public class PsiClassReferenceType extends PsiClassType.Stub {
  private final ClassReferencePointer myReference;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
    this(reference, level, collectAnnotations(reference));
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, PsiAnnotation @NotNull [] annotations) {
    super(level, annotations);
    myReference = ClassReferencePointer.constant(reference);
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    this(ClassReferencePointer.constant(reference), level, provider);
  }

  PsiClassReferenceType(@NotNull ClassReferencePointer reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    super(level, provider);
    myReference = reference;
  }

  private static PsiAnnotation @NotNull [] collectAnnotations(PsiJavaCodeReferenceElement reference) {
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
    PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
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
    return (name == null || text.contains(name)) && Objects.equals(text, getCanonicalText());
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    return getReference().getResolveScope();
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getAnnotations(true);
  }

  private PsiAnnotation[] getAnnotations(boolean merge) {
    PsiAnnotation[] annotations = super.getAnnotations();

    if (merge) {
      PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
      if (reference != null && reference.isValid() && reference.isQualified()) {
        PsiAnnotation[] embedded = collectAnnotations(reference);
        if (annotations.length > 0 && embedded.length > 0) {
          LinkedHashSet<PsiAnnotation> set = new LinkedHashSet<>();
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
  public @NotNull LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(getReference());
  }

  @Override
  public @NotNull PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    return new PsiClassReferenceType(getReference(), languageLevel, getAnnotationProvider());
  }

  @Override
  public PsiClass resolve() {
    return resolveGenerics().getElement();
  }

  private static final class DelegatingClassResolveResult implements PsiClassType.ClassResolveResult {
    private final JavaResolveResult myDelegate;

    private DelegatingClassResolveResult(@NotNull JavaResolveResult delegate) {
      myDelegate = delegate;
    }

    @Override
    public @NotNull PsiSubstitutor getSubstitutor() {
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
      PsiElement element = myDelegate.getElement();
      return element instanceof PsiClass ? (PsiClass)element : null;
    }

    @Override
    public @Nullable String getInferenceError() {
      return myDelegate instanceof PatternCandidateInfo ? ((PatternCandidateInfo)myDelegate).getInferenceError() : null;
    }
  }

  @Override
  public @NotNull ClassResolveResult resolveGenerics() {
    PsiJavaCodeReferenceElement reference = getReference();
    if (!reference.isValid()) {
      if (reference instanceof LightClassTypeReference) {
        PsiUtil.ensureValidType(((LightClassTypeReference)reference).getType());
      }
      throw new PsiInvalidElementAccessException(reference, myReference.toString() + "; augmenters=" + PsiAugmentProvider.EP_NAME.getExtensionList());
    }
    JavaResolveResult result = reference.advancedResolve(false);
    return result.getElement() == null ? ClassResolveResult.EMPTY : new DelegatingClassResolveResult(result);
  }

  @Override
  public @NotNull PsiClassType rawType() {
    PsiJavaCodeReferenceElement reference = getReference();
    PsiElement resolved = reference.resolve();
    if (resolved instanceof PsiClass) {
      PsiClass aClass = (PsiClass)resolved;
      if (!PsiUtil.typeParametersIterable(aClass).iterator().hasNext()) return this;
      PsiManager manager = reference.getManager();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
      PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor(aClass);
      return new PsiImmediateClassType(aClass, rawSubstitutor, getLanguageLevel(), getAnnotationProvider(), null);
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
  public PsiType @NotNull [] getParameters() {
    PsiJavaCodeReferenceElement reference = getReference();
    if (reference.getTypeParameterCount() == 0 &&
        reference.getParent() instanceof PsiTypeElement &&
        reference.getParent().getParent() instanceof PsiDeconstructionPattern) {
      ClassResolveResult result = resolveGenerics();
      PsiClass cls = result.getElement();
      if (cls != null && result.getInferenceError() == null) {
        return ContainerUtil.map2Array(cls.getTypeParameters(), PsiType.EMPTY_ARRAY, result.getSubstitutor().getSubstitutionMap()::get);
      }
    }
    return reference.getTypeParameters();
  }

  @Override
  public int getParameterCount() {
    PsiJavaCodeReferenceElement reference = getReference();
    if (reference.getTypeParameterCount() == 0 &&
        reference.getParent() instanceof PsiTypeElement &&
        reference.getParent().getParent() instanceof PsiDeconstructionPattern) {
      ClassResolveResult result = resolveGenerics();
      PsiClass cls = result.getElement();
      if (cls != null && result.getInferenceError() == null) {
        return cls.getTypeParameters().length;
      }
    }
    return reference.getTypeParameterCount();
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    PsiJavaCodeReferenceElement ref = getReference();
    if (!annotated) return PsiNameHelper.getPresentableText(ref);
    PsiAnnotation[] annotations;
    PsiElement qualifier = ref.getQualifier();
    String qualifierInfo = "";
    if (qualifier != null) {
      PsiAnnotation[] qualifierAnnotations = getAnnotations(false);
      if (qualifierAnnotations.length > 0 && qualifier instanceof PsiJavaCodeReferenceElement &&
          ((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass) {
        // Display qualifier if it's annotated
        qualifierInfo = PsiNameHelper.getPresentableText(qualifier.getText(), qualifierAnnotations,
                                                         ((PsiJavaCodeReferenceElement)qualifier).getTypeParameters()) + ".";
      }
      // like java.lang.@Anno String
      annotations = notNull(PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class), PsiAnnotation.EMPTY_ARRAY);
    }
    else {
      annotations = getAnnotations(false);
    }

    return qualifierInfo + PsiNameHelper.getPresentableText(ref.getReferenceName(), annotations, ref.getTypeParameters());
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return getText(annotated);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
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

  public @NotNull PsiJavaCodeReferenceElement getReference() {
    return myReference.retrieveNonNullReference();
  }

  @Override
  public @Nullable PsiElement getPsiContext() {
    return myReference.retrieveReference();
  }
}
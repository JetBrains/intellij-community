// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.TypeNullability;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotatedJavaCodeReferenceElement;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightClassTypeReference;
import com.intellij.psi.infos.PatternCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaTypeNullabilityUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.intellij.psi.impl.source.tree.JavaSharedImplUtil.filteringTypeAnnotationProvider;
import static com.intellij.util.ObjectUtils.notNull;

public class PsiClassReferenceType extends PsiClassType.Stub {
  private final ClassReferencePointer myReference;
  /**
   * Annotations that precede qualifier if qualifier exists.
   */
  private final @NotNull TypeAnnotationProvider myQualifierAnnotationsProvider;
  private TypeNullability myNullability = null;

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
    this(reference, level, collectAnnotations(reference));
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, PsiAnnotation @NotNull [] annotations) {
    super(level, annotations);
    myReference = ClassReferencePointer.constant(reference);
    myQualifierAnnotationsProvider = TypeAnnotationProvider.EMPTY;
  }

  public PsiClassReferenceType(@NotNull PsiJavaCodeReferenceElement reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider) {
    this(ClassReferencePointer.constant(reference), level, provider, TypeAnnotationProvider.EMPTY);
  }

  PsiClassReferenceType(@NotNull ClassReferencePointer reference,
                        LanguageLevel level,
                        @NotNull TypeAnnotationProvider provider,
                        @NotNull TypeAnnotationProvider qualifierAnnotationsProvider) {
    this(reference, level, provider, qualifierAnnotationsProvider, null);
  }

  private PsiClassReferenceType(@NotNull ClassReferencePointer reference, LanguageLevel level, @NotNull TypeAnnotationProvider provider,
                                @NotNull TypeAnnotationProvider qualifierAnnotationsProvider, @Nullable TypeNullability nullability) {
    super(level, provider);
    myReference = reference;
    myQualifierAnnotationsProvider = qualifierAnnotationsProvider;
    myNullability = nullability;
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
      for (PsiAnnotation annotation : myQualifierAnnotationsProvider.getAnnotations()) {
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

  @Override
  public boolean hasAnnotations() {
    if (super.hasAnnotations()) return true;
    PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
    if (reference != null && reference.isValid() && reference.isQualified()) {
      for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiAnnotation) {
          return true;
        }
      }
    }
    return false;
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
  public @NotNull TypeNullability getNullability() {
    TypeNullability nullability = myNullability;
    if (nullability == null) {
      myNullability = nullability = JavaTypeNullabilityUtil.getTypeNullability(this);
    }
    return nullability;
  }

  @Override
  public @NotNull PsiClassType annotate(@NotNull TypeAnnotationProvider provider) {
    PsiClassReferenceType annotated = (PsiClassReferenceType)super.annotate(provider);
    if (annotated != this) {
      annotated.myNullability = null;
    }
    return annotated;
  }

  @Override
  public @NotNull PsiClassType withNullability(@NotNull TypeNullability nullability) {
    if (myNullability == nullability) return this;
    return new PsiClassReferenceType(myReference, myLanguageLevel, getAnnotationProvider(), myQualifierAnnotationsProvider, nullability);
  }

  /**
   * Returns a copy of this PsiClassReferenceType with annotations from qualifierAnnotations parameter,
   * which target is {@link PsiAnnotation.TargetType#TYPE_USE}, added to qualifier annotations.
   */
  public @NotNull PsiClassReferenceType withAddedQualifierAnnotations(@NotNull PsiAnnotation @NotNull [] qualifierAnnotations) {
    TypeAnnotationProvider merged = filteringTypeAnnotationProvider(qualifierAnnotations, myQualifierAnnotationsProvider);
    return new PsiClassReferenceType(myReference, myLanguageLevel, getAnnotationProvider(), merged, myNullability);
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
    int count = reference.getTypeParameterCount();
    if (count == 0 &&
        reference.getParent() instanceof PsiTypeElement &&
        reference.getParent().getParent() instanceof PsiDeconstructionPattern) {
      ClassResolveResult result = resolveGenerics();
      PsiClass cls = result.getElement();
      if (cls != null && result.getInferenceError() == null) {
        return cls.getTypeParameters().length;
      }
    }
    return count;
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    PsiJavaCodeReferenceElement ref = getReference();
    if (!annotated) return PsiNameHelper.getPresentableText(ref);
    PsiAnnotation[] annotations;
    PsiElement qualifier = ref.getQualifier();
    String qualifierInfo = "";
    if (qualifier != null) {
      PsiAnnotation[] qualifierAnnotations = myQualifierAnnotationsProvider.getAnnotations();
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
      PsiAnnotation[] annotations =
        annotated
        ? ArrayUtil.mergeArrays(getAnnotations(false), myQualifierAnnotationsProvider.getAnnotations())
        : PsiAnnotation.EMPTY_ARRAY;
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
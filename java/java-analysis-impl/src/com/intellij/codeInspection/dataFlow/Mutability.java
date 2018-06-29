/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.InferredAnnotationsManagerImpl;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public enum Mutability {
  /**
   * Mutability is not known; probably value can be mutated
   */
  UNKNOWN("unknown", null),
  /**
   * A value is known to be mutable (e.g. elements are sometimes added to the collection)
   */
  MUTABLE("modifiable", null),
  /**
   * A value is known to be immutable. For collection no elements could be added, removed or altered (though if collection
   * contains mutable elements, they still could be mutated).
   */
  UNMODIFIABLE("unmodifiable", "org.jetbrains.annotations.Unmodifiable"),
  /**
   * A value is known to be an immutable view over a possibly mutable value: it cannot be mutated directly using this
   * reference; however subsequent reads (e.g. {@link java.util.Collection#size}) may return different results if the
   * underlying value is mutated by somebody else.
   */
  UNMODIFIABLE_VIEW("unmodifiable view", "org.jetbrains.annotations.UnmodifiableView");

  public static final @NotNull String UNMODIFIABLE_ANNOTATION = UNMODIFIABLE.myAnnotation;
  public static final @NotNull String UNMODIFIABLE_VIEW_ANNOTATION = UNMODIFIABLE_VIEW.myAnnotation;
  private final String myName;
  private final String myAnnotation;
  private final Key<CachedValue<PsiAnnotation>> myKey;

  Mutability(String name, String annotation) {
    myName = name;
    myAnnotation = annotation;
    myKey = annotation == null ? null : Key.create(annotation);
  }

  @Override
  public String toString() {
    return myName;
  }

  public boolean isUnmodifiable() {
    return this == UNMODIFIABLE || this == UNMODIFIABLE_VIEW;
  }

  @NotNull
  public Mutability union(Mutability other) {
    if (this == other) return this;
    if (this == MUTABLE || other == MUTABLE) return MUTABLE;
    if (this == UNKNOWN || other == UNKNOWN) return UNKNOWN;
    if (this == UNMODIFIABLE_VIEW || other == UNMODIFIABLE_VIEW) return UNMODIFIABLE_VIEW;
    return UNMODIFIABLE;
  }

  @Nullable
  public PsiAnnotation asAnnotation(Project project) {
    if (myAnnotation == null) return null;
    return CachedValuesManager.getManager(project).getCachedValue(project, myKey, () -> {
      PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + myAnnotation, null);
      InferredAnnotationsManagerImpl.markInferred(annotation);
      ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
      return CachedValueProvider.Result.create(annotation, ModificationTracker.NEVER_CHANGED);
    }, false);
  }

  /**
   * Returns a mutability of the supplied element, if known. The element could be a method
   * (in this case the return value mutability is returned), a method parameter
   * (the returned mutability will reflect whether the method can mutate the parameter),
   * or a field (in this case the mutability could be obtained from its initializer).
   *
   * @param owner an element to check the mutability
   * @return a Mutability enum value; {@link #UNKNOWN} if cannot be determined or specified element type is not supported.
   */
  @NotNull
  public static Mutability getMutability(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
      PsiParameterList list = (PsiParameterList)owner.getParent();
      PsiMethod method = ObjectUtils.tryCast(list.getParent(), PsiMethod.class);
      if (method != null) {
        int index = list.getParameterIndex((PsiParameter)owner);
        MutationSignature signature = MutationSignature.fromMethod(method);
        if (signature.mutatesArg(index)) {
          return MUTABLE;
        } else if (signature.preservesArg(index) &&
                   PsiTreeUtil.findChildOfAnyType(method.getBody(), PsiLambdaExpression.class, PsiClass.class) == null) {
          // If method preserves argument, it still may return a lambda which captures an argument and changes it
          // TODO: more precise check (at least differentiate parameters which are captured by lambdas or not)
          return UNMODIFIABLE_VIEW;
        }
        return UNKNOWN;
      }
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED)) {
      return UNMODIFIABLE;
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_VIEW_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED)) {
      return UNMODIFIABLE_VIEW;
    }
    if (owner instanceof PsiField && owner.hasModifierProperty(PsiModifier.FINAL)) {
      PsiField field = (PsiField)owner;
      List<PsiExpression> initializers = ContainerUtil.createMaybeSingletonList(field.getInitializer());
      if (initializers.isEmpty() && !owner.hasModifierProperty(PsiModifier.STATIC)) {
        initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      }
      initializers = StreamEx.of(initializers).flatMap(ExpressionUtils::nonStructuralChildren).toList();
      if (initializers.isEmpty()) return UNKNOWN;
      Mutability mutability = UNMODIFIABLE;
      for (PsiExpression initializer : initializers) {
        Mutability newMutability = UNKNOWN;
        if (ClassUtils.isImmutable(initializer.getType())) {
          newMutability = UNMODIFIABLE;
        } else if (initializer instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)initializer).resolveMethod();
          newMutability = method == null ? UNKNOWN : getMutability(method);
        }
        mutability = mutability.union(newMutability);
        if (!mutability.isUnmodifiable()) break;
      }
      return mutability;
    }
    return owner instanceof PsiMethodImpl ? JavaSourceInference.inferMutability((PsiMethodImpl)owner) : UNKNOWN;
  }

}

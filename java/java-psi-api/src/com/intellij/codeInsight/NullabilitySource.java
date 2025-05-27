// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Source for type nullability.
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
public /* sealed */ interface NullabilitySource {
  enum Standard implements NullabilitySource {
    /**
     * Type nullability is not specified
     */
    NONE,
    /**
     * Type nullability is mandated by language specification
     * (e.g., primitive type, or disjunction type)
     */
    MANDATED,
    /**
     * Type nullability is depicted explicitly by means of the language.
     * Currently, not possible in Java, but may be used in other languages like Kotlin.
     */
    LANGUAGE_DEFINED
  }

  /**
   * Type nullability is explicitly specified by an annotation.
   * Annotation owner is normally the type.
   */
  final class ExplicitAnnotation implements NullabilitySource {
    private final @NotNull PsiAnnotation myAnnotation;

    public ExplicitAnnotation(@NotNull PsiAnnotation annotation) { myAnnotation = annotation; }

    public @NotNull PsiAnnotation annotation() {
      return myAnnotation;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;

      ExplicitAnnotation that = (ExplicitAnnotation)o;
      return myAnnotation.equals(that.myAnnotation);
    }

    @Override
    public int hashCode() {
      return myAnnotation.hashCode();
    }

    @Override
    public String toString() {
      PsiJavaCodeReferenceElement ref = annotation().getNameReferenceElement();
      if (ref == null) return "@<unknown>";
      return "@" + ref.getReferenceName();
    }
  }

  /**
   * Type nullability is inherited from a container (member/class/package/module)
   */
  final class ContainerAnnotation implements NullabilitySource {
    private final @NotNull PsiAnnotation myAnnotation;

    public ContainerAnnotation(@NotNull PsiAnnotation annotation) {
      PsiModifierList owner = (PsiModifierList)requireNonNull(annotation.getOwner(), "Annotation has no owner");
      PsiModifierListOwner parent = (PsiModifierListOwner)requireNonNull(owner.getParent(), "Modifier list has no parent");
      if (parent.getModifierList() != owner) {
        throw new IllegalStateException("Modifier list parent is incorrect");
      }
      myAnnotation = annotation;
    }

    public @NotNull PsiModifierListOwner container() {
      PsiModifierList owner = (PsiModifierList)requireNonNull(myAnnotation.getOwner());
      PsiModifierListOwner parent = (PsiModifierListOwner)requireNonNull(owner.getParent());
      if (parent.getModifierList() != owner) {
        throw new IllegalStateException("Modifier list parent is incorrect");
      }
      return parent;
    }

    public @NotNull PsiAnnotation annotation() {
      return myAnnotation;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      ContainerAnnotation that = (ContainerAnnotation)o;
      return myAnnotation.equals(that.myAnnotation);
    }

    @Override
    public int hashCode() {
      return myAnnotation.hashCode();
    }

    @Override
    public String toString() {
      PsiModifierListOwner container = container();
      String containerInfo;
      if (container instanceof PsiClass) {
        containerInfo = "class " + ((PsiClass)container).getName();
      }
      else if (container instanceof PsiField) {
        containerInfo = "field " + ((PsiField)container).getName();
      }
      else if (container instanceof PsiMethod) {
        containerInfo = "method " + ((PsiMethod)container).getName();
      }
      else if (container instanceof PsiPackage) {
        containerInfo = "package " + ((PsiPackage)container).getName();
      }
      else if (container instanceof PsiJavaModule) {
        containerInfo = "module " + ((PsiJavaModule)container).getName();
      }
      else {
        containerInfo = container.getClass().getSimpleName();
      }
      PsiJavaCodeReferenceElement ref = annotation().getNameReferenceElement();
      String annotationInfo = ref == null ? "@<unknown>" : "@" + ref.getReferenceName();
      return annotationInfo + " on " + containerInfo;
    }
  }

  final class MultiSource implements NullabilitySource {
    private final @NotNull Set<@NotNull NullabilitySource> mySources;

    MultiSource(@NotNull Set<@NotNull NullabilitySource> sources) {
      if (sources.size() <= 1) {
        throw new IllegalArgumentException("MultiSource must have at least two sources");
      }
      mySources = sources;
    }

    public @NotNull Set<@NotNull NullabilitySource> sources() {
      return mySources;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;

      MultiSource source = (MultiSource)o;
      return mySources.equals(source.mySources);
    }

    @Override
    public int hashCode() {
      return mySources.hashCode();
    }

    @Override
    public String toString() {
      return mySources.toString();
    }
  }

  /**
   * @param sources sources to combine
   * @return combined source, or {@link Standard#NONE} if no sources are specified
   */
  static @NotNull NullabilitySource multiSource(@NotNull Collection<@NotNull NullabilitySource> sources) {
    Set<NullabilitySource> set = new LinkedHashSet<>();
    for (NullabilitySource source : sources) {
      if (source instanceof MultiSource) {
        set.addAll(((MultiSource)source).sources());
      }
      if (source == Standard.NONE) continue;
      set.add(source);
    }
    if (set.isEmpty()) return Standard.NONE;
    if (set.size() == 1) return set.iterator().next();
    return new MultiSource(set);
  }
}

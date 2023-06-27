// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiClass;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Node in the graph corresponding to a Java or Kotlin class
 */
public interface RefClass extends RefJavaElement, RefOverridable {

  /** @return the direct super classes of this class */
  @NotNull
  Set<RefClass> getBaseClasses();

  /** @return the direct subclasses of this class. */
  @NotNull
  Set<RefClass> getSubClasses();

  /** @return the constructors of this class */
  @NotNull
  List<RefMethod> getConstructors();

  /** @return the fields of this class */
  default List<RefField> getFields() {
    return Collections.emptyList();
  }

  @NotNull
  Set<RefElement> getInTypeReferences();

  @Deprecated(forRemoval = true)
  @NotNull
  default Set<RefElement> getInstanceReferences() {
    throw new UnsupportedOperationException();
  }

  RefMethod getDefaultConstructor();

  @NotNull
  List<RefMethod> getLibraryMethods();

  /**
   * @return true is this class is anonymous, false otherwise.
   */
  boolean isAnonymous();

  /**
   * @return true if this class is an interface, false otherwise.
   */
  boolean isInterface();

  /**
   * @return true if this class is a record, false otherwise.
   */
  default boolean isRecord() {
    return false;
  }

  /**
   * @return true if this is an annotation type declaration, false otherwise
   */
  default boolean isAnnotationType() {
    return false;
  }

  /**
   * In Java a utility class has only static methods or fields and no non-private constructors or constructors with parameters.
   * However in Kotlin utility classes (Objects) follow singleton pattern.
   * @return true if this class is a utility class, false otherwise.
   */
  boolean isUtilityClass();

  /**
   * @return true if this class is abstract, false otherwise.
   */
  boolean isAbstract();

  boolean isApplet();

  boolean isServlet();

  boolean isTestCase();

  /**
   * @return true if this class is a local class, false otherwise.
   */
  boolean isLocalClass();

  /** @return true if this class is an enum class, false otherwise. */
  default boolean isEnum() {
    return false;
  }

  @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
  @Deprecated(forRemoval = true)
  default boolean isSelfInheritor(PsiClass psiClass) {
    throw new UnsupportedOperationException();
  }

  default boolean isSelfInheritor(@NotNull UClass uClass) {
    return isSelfInheritor(uClass.getJavaPsi());
  }

  @Override
  default UClass getUastElement() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  default PsiClass getElement() {
    return ObjectUtils.tryCast(getPsiElement(), PsiClass.class);
  }

  @Override
  default @NotNull Collection<? extends RefOverridable> getDerivedReferences() {
    return getSubClasses();
  }
  
  @Override
  default void addDerivedReference(@NotNull RefOverridable reference) {
    // do nothing
  }
}

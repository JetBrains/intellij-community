// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiClass;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RefClass extends RefJavaElement, RefOverridable {

  @NotNull
  Set<RefClass> getBaseClasses();

  @NotNull
  Set<RefClass> getSubClasses();

  @NotNull
  List<RefMethod> getConstructors();

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

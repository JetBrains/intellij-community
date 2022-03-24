// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  boolean isAnonymous();

  boolean isInterface();

  boolean isUtilityClass();

  boolean isAbstract();

  boolean isApplet();

  boolean isServlet();

  boolean isTestCase();

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

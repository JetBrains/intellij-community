// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;

import java.util.Collection;

/**
 * A node in the reference graph corresponding to a Java method or Kotlin function.
 *
 * @author anna
 */
public interface RefMethod extends RefJavaElement, RefOverridable {
  /**
   * Returns the collection of the direct super methods of this method in the
   * analysis scope.
   *
   * @return the collection of super methods.
   * @see com.intellij.psi.PsiMethod#findSuperMethods()
   * @see #hasSuperMethods
   */
  @NotNull Collection<RefMethod> getSuperMethods();

  /**
   * Returns the collection of the overriding methods of this method in the
   * analysis scope.
   *
   * @return the collection of overriding methods.
   */
  @NotNull Collection<RefMethod> getDerivedMethods();

  /**
   * Checks if this method has a body (that is, not a method of an interface or an abstract
   * method).
   *
   * @return true if the method has a body, false otherwise.
   */
  boolean hasBody();

  /**
   * Checks if the method has no body or its body contains no statements besides
   * (possibly) a call to its super method.
   *
   * @return true if the element has no body or the body is empty, false otherwise.
   */
  boolean isBodyEmpty();

  /**
   * Checks if the method has a body which consists only of the call to its super method.
   *
   * @return true if the method only calls its super method, false otherwise.
   */
  boolean isOnlyCallsSuper();

  /**
   * Checks if the method is a record accessor method.
   *
   * @return true if the method is a record accessor method, false otherwise.
   */
  default boolean isRecordAccessor() {
    return false;
  }

  /**
   * Checks if the method is a test method in a testcase class.
   *
   * @return true if the method is a test method, false otherwise.
   */
  boolean isTestMethod();

  /**
   * Checks if the signature of the method matches the signature of the standard {@code main}
   * or {@code premain} method.
   *
   * @return true if the method can be a main function of the application, false otherwise.
   */
  boolean isAppMain();

  /**
   * Checks if the method has super methods either in the analysis scope or outside of it.
   *
   * @return true if the method has super methods, false otherwise.
   * @see #getSuperMethods()
   */
  boolean hasSuperMethods();

  /**
   * Checks if the method overrides a method outside the current analysis scope.
   *
   * @return true if the method overrides a method not in the analysis scope, false otherwise.
   */
  boolean isExternalOverride();

  /**
   * Checks if the method is a constructor.
   *
   * @return true if the method is a constructor, false otherwise.
   */
  boolean isConstructor();

  /**
   * Checks if the method is abstract.
   *
   * @return true if the method is abstract, false otherwise.
   */
  boolean isAbstract();

  /**
   * Checks if the return value of the method is used by any of its callers.
   *
   * @return true if the method return value is used, false otherwise.
   */
  boolean isReturnValueUsed();

  /**
   * If the method always returns the same value, returns that value (the name of a static
   * final field or the text of a literal expression). Otherwise, returns null.
   *
   * @return the method return value or null if it's different or impossible to determine.
   */
  @Nullable String getReturnValueIfSame();

  /**
   * Returns the list of exceptions which are included in the {@code throws} list
   * of the method but cannot be actually thrown. 
   * <p>
   * To return valid results, requires com.intellij.codeInspection.unneededThrows.RedundantThrowsGraphAnnotator.
   * (Dbl) Annotator registration is possible in {@link GlobalInspectionTool#initialize(com.intellij.codeInspection.GlobalInspectionContext)}
   * or {@link GlobalInspectionTool#getAnnotator(RefManager)} 
   *
   * @return the array of exceptions declared but not thrown, or null if there are no
   * such exceptions.
   */
  PsiClass @Nullable [] getUnThrownExceptions();

  /**
   * Returns the list of reference graph nodes for the method parameters.
   *
   * @return the method parameters.
   */
  RefParameter @NotNull [] getParameters();

  /**
   * Returns the class to which the method belongs.
   *
   * @return the class instance.
   */
  @Nullable
  RefClass getOwnerClass();

  @Deprecated
  @Override
  default PsiModifierListOwner getElement() {
    return ObjectUtils.tryCast(getPsiElement(), PsiModifierListOwner.class);
  }

  @Override
  default UMethod getUastElement() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  default Collection<? extends RefOverridable> getDerivedReferences() {
    return getDerivedMethods();
  }

  @Override
  default void addDerivedReference(@NotNull RefOverridable reference) {
    // do nothing
  }

  boolean isCalledOnSubClass();
}

/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A node in the reference graph corresponding to a Java method.
 *
 * @author anna
 * @since 6.0
 */
public interface RefMethod extends RefElement{
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
   * Checks if the method is a test method in a testcase class.
   *
   * @return true if the method is a test method, false otherwise.
   */
  boolean isTestMethod();

  /**
   * Checks if the signature of the method matches the signature of the standard <code>main</code>
   * or <code>premain</code> method.
   *
   * @return true if the method can be a main function of the application, false otherwise.
   */
  boolean isAppMain();
  boolean isEjbDeclaration();
  boolean isEjbImplementation();

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
   * Returns the list of exceptions which are included in the <code>throws</code> list
   * of the method but cannot be actually thrown.
   *
   * @return the list of exceptions declared but not thrown, or null if there are no
   * such exceptions.
   */
  @Nullable PsiClass[] getUnThrownExceptions();

  /**
   * Returns the list of reference graph nodes for the method parameters.
   *
   * @return the method parameters.
   */
  @NotNull RefParameter[] getParameters();

  /**
   * Returns the class to which the method belongs.
   *
   * @return the class instance.
   */
  RefClass getOwnerClass();

  PsiModifierListOwner getElement();
}

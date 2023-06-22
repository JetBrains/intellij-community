// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a Java method or constructor.
 *
 * @see PsiClass#getMethods()
 */
public interface PsiMethod extends PsiMember, PsiNameIdentifierOwner, PsiModifierListOwner, PsiDocCommentOwner, PsiTypeParameterListOwner,
                                   PomRenameableTarget<PsiElement>, PsiTarget, PsiParameterListOwner, JvmMethod {
  /**
   * The empty array of PSI methods which can be reused to avoid unnecessary allocations.
   */
  PsiMethod[] EMPTY_ARRAY = new PsiMethod[0];

  ArrayFactory<PsiMethod> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiMethod[count];

  /**
   * Returns the return type of the method.
   *
   * @return the method return type, or null if the method is a constructor.
   */
  @Override
  @Nullable
  PsiType getReturnType();

  /**
   * Returns the type element for the return type of the method.
   *
   * @return the type element for the return type, or null if the method is a constructor.
   */
  @Nullable
  PsiTypeElement getReturnTypeElement();

  /**
   * Returns the parameter list for the method. 
   * For Java record compact constructor, a non-physical list is returned,
   * which contains implicitly defined parameters based on the record components.
   *
   * @return the parameter list instance.
   */
  @Override
  @NotNull
  PsiParameterList getParameterList();

  /**
   * Returns the list of thrown exceptions for the method.
   *
   * @return the list of thrown exceptions instance.
   */
  @NotNull
  PsiReferenceList getThrowsList();

  /**
   * Returns the body of the method.
   *
   * @return the method body, or null if the method has no body (e.g., abstract or native),
   * or belongs to a compiled class.
   */
  @Override
  @Nullable
  PsiCodeBlock getBody();

  /**
   * Checks if the method is a constructor.
   * In Java PSI, the method is considered to be a constructor 
   * if it lacks the return type; even if its name differs from the 
   * class name (in this case, a highlighting error will be displayed).
   *
   * @return true if the method is a constructor, false otherwise
   */
  @Override
  boolean isConstructor();

  /**
   * Checks if the method accepts a variable number of arguments.
   *
   * @return true if the method is varargs, false otherwise.
   */
  @Override
  boolean isVarArgs();

  /**
   * Returns the signature of this method, using the specified substitutor to specify
   * values of generic type parameters.
   *
   * @param substitutor the substitutor.
   * @return the method signature instance.
   */
  @NotNull
  MethodSignature getSignature(@NotNull PsiSubstitutor substitutor);

  /**
   * Returns the name identifier for the method.
   *
   * @return the name identifier instance.
   */
  @Override
  @Nullable
  PsiIdentifier getNameIdentifier();

  /**
   * Searches the superclasses and base interfaces of the containing class to find
   * the methods which this method overrides or implements. Can return multiple results
   * if the base class and/or one or more of the implemented interfaces have a method
   * with the same signature. If the overridden method in turn overrides another method,
   * only the directly overridden method is returned.
   *
   * @return the array of super methods, or an empty array if no methods are found.
   */
  PsiMethod @NotNull [] findSuperMethods();

  /**
   * Searches the superclasses and base interfaces of the containing class to find
   * the methods which this method overrides or implements, optionally omitting
   * the accessibility check. Can return multiple results if the base class and/or
   * one or more of the implemented interfaces have a method with the same signature.
   * If the overridden method in turn overrides another method, only the directly
   * overridden method is returned.
   *
   * @param checkAccess if false, the super methods are searched even if this method
   *                    is private. If true, an empty result list is returned for private methods.
   * @return the array of super methods, or an empty array if no methods are found.
   */
  PsiMethod @NotNull [] findSuperMethods(boolean checkAccess);

  /**
   * Searches the superclasses and base interfaces of the specified class to find
   * the methods which this method can override or implement. Can return multiple results
   * if the base class and/or one or more of the implemented interfaces have a method
   * with the same signature.
   *
   * @param parentClass the class to search for super methods.
   * @return the array of super methods, or an empty array if no methods are found.
   */
  PsiMethod @NotNull [] findSuperMethods(PsiClass parentClass);

  /**
   * Searches the superclasses and base interfaces of the containing class to find
   * static and instance methods with the signature matching the signature of this method.
   * Can return multiple results if the base class and/or one or more of the implemented
   * interfaces have a method with the same signature. If the overridden method in turn
   * overrides another method, only the directly overridden method is returned.
   *
   * @param checkAccess if false, the super methods are searched even if this method
   *                    is private. If true, an empty result list is returned for private methods.
   * @return the list of matching method signatures, or an empty array if no methods are found.
   */
  @NotNull
  List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess);

  /**
   * Returns the method in the deepest base superclass or interface of the containing class which
   * this method overrides or implements.
   *
   * @return the overridden or implemented method, or null if this method does not override
   *         or implement any other method.
   * @deprecated use {@link #findDeepestSuperMethods()} instead
   */
  @Deprecated
  @Nullable
  PsiMethod findDeepestSuperMethod();

  PsiMethod @NotNull [] findDeepestSuperMethods();

  @Override
  @NotNull
  PsiModifierList getModifierList();

  /**
   * @return the name of the method, as visible in the source code.
   * For well-formed constructor, the name of the containing class is returned.
   */
  @Override
  @NotNull
  @NlsSafe
  String getName();

  @Override
  PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException;

  @NotNull
  HierarchicalMethodSignature getHierarchicalMethodSignature();

  @Override
  default boolean hasParameters() {
    return !getParameterList().isEmpty();
  }

  @Override
  default JvmParameter @NotNull [] getParameters() {
    return getParameterList().getParameters();
  }

  @Override
  default JvmReferenceType @NotNull [] getThrowsTypes() {
    return getThrowsList().getReferencedTypes();
  }
}

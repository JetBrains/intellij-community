// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Medvedev Max
 */
public interface JVMElementFactory {
  /**
   * Creates an empty class with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createClass(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createInterface(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createEnum(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier or {@code type} represents an invalid type.
   */
  @NotNull
  PsiField createField(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier or {@code type} represents an invalid type.
   */
  @NotNull
  PsiMethod createMethod(@NotNull String name, PsiType returnType) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type in the given context.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier or {@code type} represents an invalid type.
   */
  @NotNull
  PsiMethod createMethod(@NotNull String name, PsiType returnType, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   */
  @NotNull
  PsiMethod createConstructor();

  /**
   * Creates an empty class initializer block.
   */
  @NotNull
  PsiClassInitializer createClassInitializer();

  /**
   * Creates a parameter with the specified name and type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier or {@code type} represents an invalid type.
   */
  @NotNull
  PsiParameter createParameter(@NotNull String name, PsiType type) throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type in the given context.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier or {@code type} represents an invalid type.
   */
  PsiParameter createParameter(@NotNull String name, PsiType type, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  @NotNull
  PsiParameterList createParameterList(String @NotNull [] names, PsiType @NotNull [] types) throws IncorrectOperationException;

  @NotNull
  PsiMethod createMethodFromText(String text, @Nullable PsiElement context);

  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  PsiElement createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a new reference element from the class type.
   */
  @NotNull
  PsiElement createReferenceElementByType(PsiClassType type);

  /**
   * Creates an empty type parameter list.
   */
  @NotNull
  PsiTypeParameterList createTypeParameterList();

  /**
   * Creates new type parameter with the specified name and super types.
   */
  @NotNull
  PsiTypeParameter createTypeParameter(@NotNull String name, PsiClassType @NotNull [] superTypes);

  /**
   * Creates a class type for the specified class.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass);

  /**
   * Creates an empty annotation type with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createAnnotationType(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty constructor with a given name.
   */
  @NotNull
  PsiMethod createConstructor(@NotNull String name);

  /**
   * Creates an empty constructor with a given name in the given context.
   */
  @NotNull
  PsiMethod createConstructor(@NotNull String name, @Nullable PsiElement context);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor);

  /**
   * Creates a class type for the specified class, using the specified substitutor and language level
   * to replace generic type parameters on the class.
   *
   * @param resolve       the class for which the class type is created.
   * @param substitutor   the substitutor to use.
   * @param languageLevel to memorize language level for allowing/prohibiting boxing/unboxing.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel);

  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType parameters);

  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType... parameters);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters with their corresponding raw types.
   */
  @NotNull
  PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   */
  @NotNull
  PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name, or null if {@code name} is not a valid primitive type name.
   */
  @Nullable
  PsiPrimitiveType createPrimitiveType(@NotNull String text);

  /**
   * The same as {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   * with {@link GlobalSearchScope#allScope(com.intellij.openapi.project.Project)}.
   */
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified search scope.
   */
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates doc comment from text.
   */
  @NotNull
  PsiDocComment createDocCommentFromText(@NotNull String text);

  /**
   * Checks whether the name is a valid class name in the current language.
   */
  boolean isValidClassName(@NotNull String name);

  /**
   * Checks whether the name is a valid method name in the current language.
   */
  boolean isValidMethodName(@NotNull String name);

  /**
   * Checks whether the name is a valid parameter name in the current language.
   */
  boolean isValidParameterName(@NotNull String name);

  /**
   * Checks whether the name is a valid field name in the current language.
   */
  boolean isValidFieldName(@NotNull String name);

  /**
   * Checks whether the name is a valid local variable name in the current language.
   */
  boolean isValidLocalVariableName(@NotNull String name);
}
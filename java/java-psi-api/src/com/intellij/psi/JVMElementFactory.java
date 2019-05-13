/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
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
   * @param name the name of the class to create.
   * @return the created class instance.
   * @throws IncorrectOperationException
   *          if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createClass(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @param name the name of the interface to create.
   * @return the created interface instance.
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createInterface(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @param name the name of the enum to create.
   * @return the created enum instance.
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createEnum(@NotNull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @param name the name of the field to create.
   * @param type the type of the field to create.
   * @return the created field instance.
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @NotNull
  PsiField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @param name       the name of the method to create.
   * @param returnType the return type of the method to create.
   * @return the created method instance.
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @NotNull
  PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType) throws IncorrectOperationException;

  @NotNull
  PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   *
   * @return the created constructor instance.
   */
  @NotNull
  PsiMethod createConstructor();

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @NotNull
  PsiClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type.
   *
   * @param name the name of the parameter to create.
   * @param type the type of the parameter to create.
   * @return the created parameter instance.
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @NotNull
  PsiParameter createParameter(@NotNull @NonNls String name, PsiType type) throws IncorrectOperationException;
  PsiParameter createParameter(@NotNull @NonNls String name, PsiType type, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @param names the array of names for the parameters to create.
   * @param types the array of types for the parameters to create.
   * @return the created parameter list.
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  @NotNull
  PsiParameterList createParameterList(@NotNull @NonNls String[] names, @NotNull PsiType[] types) throws IncorrectOperationException;

  @NotNull
  PsiMethod createMethodFromText(String text, @Nullable PsiElement context);

  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  PsiElement createExpressionFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates new reference element by type
   * @param type the type for which reference element should be created
   * @return the reference element
   */
  @NotNull
  PsiElement createReferenceElementByType(PsiClassType type);

  /**
   * Creates empty type parameter list
   * @return the empty type parameter list
   */
  @NotNull
  PsiTypeParameterList createTypeParameterList();

  /**
   * Creates new type parameter with the specified name and super types
   * @param name the name which the type parameter should have
   * @param superTypes the super types of the type parameter
   * @return the new type parameter
   */
  @NotNull
  PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes);

  /**
   * Creates a class type for the specified class.
   *
   * @param aClass the class for which the class type is created.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass);

  /**
   * Creates an empty annotation type with the specified name.
   *
   * @param name the name of the annotation type to create.
   * @return the created annotation type instance.
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createAnnotationType(@NotNull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates an empty constructor with a given name.
   *
   * @param name the name of the constructor to create.
   * @return the created constructor instance.
   */
  @NotNull
  PsiMethod createConstructor(@NotNull @NonNls String name);
  PsiMethod createConstructor(@NotNull @NonNls String name, PsiElement context);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve     the class for which the class type is created.
   * @param substitutor the substitutor to use.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve       the class for which the class type is created.
   * @param substitutor   the substitutor to use.
   * @param languageLevel to memorize language level for allowing/prohibiting boxing/unboxing.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @NotNull LanguageLevel languageLevel);

  /** @deprecated use {@link PsiType#annotate(TypeAnnotationProvider)} (to be removed in IDEA 18) */
  @Deprecated
  PsiClassType createType(@NotNull PsiClass resolve,
                          @NotNull PsiSubstitutor substitutor,
                          @NotNull LanguageLevel languageLevel,
                          @NotNull PsiAnnotation[] annotations);

  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType parameters);

  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType... parameters);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters
   * with their corresponding raw types.
   *
   * @param owner the class or method for which the substitutor is created.
   * @return the substitutor instance.
   */
  @NotNull
  PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   *
   * @param map the type parameter to type map used by the substitutor.
   * @return the substitutor instance.
   */
  @NotNull
  PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name.
   *
   * @param text the name of a Java primitive type (for example, {@code int})
   * @return the primitive type instance, or null if {@code name} is not a valid
   *         primitive type name.
   */
  @Nullable
  PsiPrimitiveType createPrimitiveType(@NotNull String text);

  /**
   * The same as {@link #createTypeByFQClassName(String, com.intellij.psi.search.GlobalSearchScope)}
   * with {@link com.intellij.psi.search.GlobalSearchScope#allScope(com.intellij.openapi.project.Project)}.
   *
   * @param qName the full-qualified name of the class to create the reference to.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified
   * search scope.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the class type instance.
   */
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates doc comment from text
   */
  @NotNull
  PsiDocComment createDocCommentFromText(@NotNull String text);

  /**
   * Checks whether name is a valid class name in the current language
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidClassName(@NotNull String name);

  /**
   * Checks whether name is a valid method name in the current language
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidMethodName(@NotNull String name);

  /**
   * Checks whether name is a valid parameter name in the current language
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidParameterName(@NotNull String name);

  /**
   * Checks whether name is a valid field name in the current language
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidFieldName(@NotNull String name);

  /**
   * Checks whether name is a valid local variable name in the current language
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidLocalVariableName(@NotNull String name);
}


/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a mapping between type parameters and their values.
 *
 * @author ik, dsl
 * @see com.intellij.psi.JavaResolveResult#getSubstitutor()
 */
public interface PsiSubstitutor {
  Key<PsiSubstitutor> KEY = KeyWithDefaultValue.create("SUBSTITUTOR", EmptySubstitutor.getInstance());

  /**
   * Empty, or natural, substitutor. For any type parameter {@code T},
   * substitutes type {@code T}.
   * <b>Example:</b> consider class {@code List<E>}. {@code this}
   * inside class {@code List} has type List with EMPTY substitutor.
   */
  @NotNull
  PsiSubstitutor EMPTY = EmptySubstitutor.getInstance();
  @NotNull
  PsiSubstitutor UNKNOWN = EMPTY;

  /**
   * Returns a mapping that this substitutor contains for a given type parameter.
   * Does not perform bounds promotion
   *
   * @param typeParameter the parameter to return the mapping for.
   * @return the mapping for the type parameter, or {@code null} for a raw type.
   */
  @Nullable
  PsiType substitute(@NotNull PsiTypeParameter typeParameter);

  /**
   * Substitutes type parameters occurring in {@code type} with their values.
   * If value for type parameter is {@code null}, appropriate erasure is returned.
   *
   * @param type the type to substitute the type parameters for.
   * @return the result of the substitution.
   */
  PsiType substitute(@Nullable PsiType type);

  //Should be used with great care, be sure to prevent infinite recursion that could arise
  // from the use of recursively bounded type parameters
  PsiType substituteWithBoundsPromotion(@NotNull PsiTypeParameter typeParameter);

  /**
   * Creates a substitutor instance which provides the specified parameter to type mapping in addition
   * to mappings contained in this substitutor.
   *
   * @param classParameter the parameter which is mapped.
   * @param mapping        the type to which the parameter is mapped.
   * @return the new substitutor instance.
   */
  @NotNull
  PsiSubstitutor put(@NotNull PsiTypeParameter classParameter, PsiType mapping);

  /**
   * Creates a substitutor instance which maps the type parameters of the specified class to the
   * specified types in addition to mappings contained in this substitutor.
   *
   * @param parentClass the class whose parameters are mapped.
   * @param mappings    the types to which the parameters are mapped.
   * @return the new substitutor instance.
   */
  @NotNull
  PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings);

  /**
   * Creates a substitutor instance containing all mappings from this substitutor and the
   * specified substitutor.
   *
   * @param another the substitutor to get the mappings from.
   * @return the new substitutor instance.
   */
  @NotNull
  PsiSubstitutor putAll(@NotNull PsiSubstitutor another);

  /**
   * Returns the map from type parameters to types used for substitution by this substitutor.
   *
   * @return the substitution map instance.
   */
  @NotNull
  Map<PsiTypeParameter, PsiType> getSubstitutionMap();

  /**
   * Checks if all types which the substitutor can substitute are valid.
   *
   * @return true if all types are valid, false otherwise.
   * @see PsiType#isValid()
   */
  boolean isValid();

  /**
   * If this substitutor is not valid, throws an exception with some diagnostics
   */
  void ensureValid();
}

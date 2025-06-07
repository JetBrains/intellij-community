// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a mapping between type parameters and their values.
 *
 * @author ik, dsl
 * @see JavaResolveResult#getSubstitutor()
 */
public interface PsiSubstitutor {
  /**
   * Empty, or natural, substitutor. For any type parameter {@code T},
   * substitutes type {@code T}.
   * <b>Example:</b> consider class {@code List<E>}. {@code this}
   * inside class {@code List} has type List with EMPTY substitutor.
   */
  @NotNull
  PsiSubstitutor EMPTY = EmptySubstitutor.getInstance();

  Key<PsiSubstitutor> KEY = KeyWithDefaultValue.create("SUBSTITUTOR", EMPTY);

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
  @Contract(pure = true)
  PsiType substitute(@NotNull PsiTypeParameter typeParameter);

  /**
   * Substitutes type parameters occurring in {@code type} with their values.
   * If value for type parameter is {@code null}, appropriate erasure is returned.
   *
   * @param type the type to substitute the type parameters for.
   * @return the result of the substitution.
   */
  @Contract(pure = true, value = "null -> null")
  PsiType substitute(@Nullable PsiType type);

  /**
   * @return true if this substitutor has at least one raw substitution
   */
  default boolean hasRawSubstitution() {
    return getSubstitutionMap().containsValue(null);
  }

  //Should be used with great care, be sure to prevent infinite recursion that could arise
  // from the use of recursively bounded type parameters
  @Contract(pure = true)
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
  @Contract(pure = true)
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
  @Contract(pure = true)
  PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings);

  /**
   * Creates a substitutor instance containing all mappings from this substitutor and the
   * specified substitutor.
   *
   * @param another the substitutor to get the mappings from.
   * @return the new substitutor instance.
   */
  @NotNull
  @Contract(pure = true)
  PsiSubstitutor putAll(@NotNull PsiSubstitutor another);

  /**
   * Creates a substitutor instance containing all mappings from this substitutor and the
   * specified map.
   *
   * @param map a map which contains additional mappings
   * @return the new substitutor instance.
   */
  @Contract(pure = true)
  default @NotNull PsiSubstitutor putAll(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    return putAll(createSubstitutor(map));
  }

  /**
   * Returns the map from type parameters to types used for substitution by this substitutor.
   *
   * @return the substitution map instance.
   */
  @NotNull
  @Contract(pure = true)
  Map<PsiTypeParameter, PsiType> getSubstitutionMap();

  /**
   * Create a substitutor from the supplied map
   * @param map a map to create a substitutor from
   * @return a substitutor backed by the supplied map
   */
  static @NotNull PsiSubstitutor createSubstitutor(@Nullable Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    if (map == null || map.isEmpty()) return EMPTY;
    return EMPTY.putAll(map);
  }

  /**
   * Checks if all types which the substitutor can substitute are valid.
   *
   * @return true if all types are valid, false otherwise.
   * @see PsiType#isValid()
   */
  @Contract(pure = true)
  boolean isValid();

  /**
   * If this substitutor is not valid, throws an exception with some diagnostics
   */
  void ensureValid();
}

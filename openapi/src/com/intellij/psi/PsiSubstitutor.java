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
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents a mapping between type parameters and their values.
 *
 * @author ik, dsl
 */
public interface PsiSubstitutor {
  /**
   * Empty, or natural, substitutor. For any type parameter <code>T</code>,
   * substitues type <code>T</code>.
   * <b>Example:</b> consider class <code>List&lt;E&gt;</code>. <code>this</code>
   * inside class <code>List</code> has type List with EMPTY substitutor.
   */
  PsiSubstitutor EMPTY = EmptySubstitutor.getInstance();
  PsiSubstitutor UNKNOWN = EMPTY;

  /**
   * Returns a mapping that this substitutor contains for a given type parameter.
   * Does not perform bounds promotion
   *
   * @param typeParameter the parameter to return the mapping for.
   * @return the mapping for the type parameter, or <code>null</code> for a raw type.
   */
  @Nullable
  PsiType substitute(PsiTypeParameter typeParameter);

  /**
   * Substitutes type parameters occuring in <code>type</code> with their values.
   * If value for type parameter is <code>null<code>, appropriate erasure is returned.
   *
   * @param type the type to substitute the type parameters for.
   * @return the result of the substitution.
   */
  PsiType substitute(PsiType type);

  /**
   * Substitutes type parameters occuring in <code>type</code> with their values.
   * If value for type parameter is <code>null<code>, appropriate erasure is returned.
   * If value of a <b>class</b> type parameter is a wildcard type, captures it in {@link PsiCapturedWildcardType}
   *
   * @param type the type to substitute the type parameters for.
   * @return the result of the substitution.
   */
  PsiType substituteAndCapture(PsiType type);

  /**
   * Substitutes type parameters occuring in <code>type</code> with their values.
   * If value for type parameter is <code>null<code>, appropriate erasure is returned.
   * If value of a type parameter is a wildcard type, captures it in {@link PsiCapturedWildcardType}
   *
   * @param type the type to substitute the type parameters for.
   * @return the result of the substitution.
   */
  PsiType substituteAndFullCapture(PsiType type);

  PsiType substituteWithoutBoundsPromotion(PsiType type);

  //Should be used with great care, be sure to prevent infinite recursion that could arise
  // from the use of recursively bounded type parameters
  PsiType substituteWithBoundsPromotion(PsiTypeParameter typeParameter);

  /**
   * Creates a substitutor instance which provides the specified parameter to type mapping in addition
   * to mappings contained in this substitutor.
   *
   * @param classParameter the parameter which is mapped.
   * @param mapping        the type to which the parameter is mapped.
   * @return the new substitutor instance.
   */
  PsiSubstitutor put(PsiTypeParameter classParameter, PsiType mapping);

  /**
   * Creates a substitutor instance which maps the type parameters of the specified class to the
   * specified types in addition to mappings contained in this substitutor.
   *
   * @param parentClass the class whose parameters are mapped.
   * @param mappings    the types to which the parameters are mapped.
   * @return the new substitutor instance.
   */
  PsiSubstitutor putAll(PsiClass parentClass, PsiType[] mappings);

  /**
   * Creates a substitutor instance containing all mappings from this substitutor and the
   * specified substitutor.
   *
   * @param another the substitutor to get the mappings from.
   * @return the new substitutor instance.
   */
  PsiSubstitutor putAll(PsiSubstitutor another);

  /**
   * Returns the map from type parameters to types used for substution by this substitutor.
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
}

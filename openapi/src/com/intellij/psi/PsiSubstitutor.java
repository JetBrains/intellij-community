/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import java.util.Map;

/**
 * Represents a mapping between type parameters and their values.
 * @author ik, dsl
 */
public interface PsiSubstitutor{
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
   * Returns <code>null</code> for raw type.
   */
  PsiType substitute(PsiTypeParameter typeParameter);

  /**
   * Substitutes type parameters occuring in <code>type</code> with their values.
   * If value for type parameter is <code>null<code>, appropriate erasure is returned.
   * @param type
   * @return
   */
  PsiType substitute(PsiType type);

  PsiType substituteAndCapture(PsiType type);

  PsiSubstitutor put(PsiTypeParameter classParameter, PsiType mapping);
  PsiSubstitutor putAll(PsiClass parentClass, PsiType[] mappings);
  PsiSubstitutor putAll(PsiSubstitutor another);

  PsiSubstitutor merge(PsiSubstitutor other);

  Map<PsiTypeParameter, PsiType> getSubstitutionMap();

  boolean isValid();
}

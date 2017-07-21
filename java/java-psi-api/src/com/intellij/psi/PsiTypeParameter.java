/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the type parameter of a generic class, interface, method or constructor.
 *
 * @author dsl
 */
public interface PsiTypeParameter extends PsiClass, PsiAnnotationOwner, JvmTypeParameter {
  /**
   * The empty array of PSI type parameters which can be reused to avoid unnecessary allocations.
   */
  PsiTypeParameter[] EMPTY_ARRAY = new PsiTypeParameter[0];

  ArrayFactory<PsiTypeParameter> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiTypeParameter[count];

  /**
   * Returns the extends list of the type parameter.
   *
   * @return the extends list. For this particular kind of classes it never returns null.
   */
  @Override
  @NotNull
  PsiReferenceList getExtendsList();

  /**
   * Returns the element which is parameterized by the type parameter.
   *
   * @return the type parameter owner instance.
   */
  @Nullable
  @Override
  PsiTypeParameterListOwner getOwner();

  /**
   * Returns the position of this type parameter in the type parameter list of the owner element.
   *
   * @return the type parameter position.
   */
  int getIndex();

  @NotNull
  @Override
  default PsiAnnotation[] getAnnotations() {
    return PsiClass.super.getAnnotations();
  }

  @NotNull
  @Override
  default JvmReferenceType[] getBounds() {
    return getExtendsList().getReferencedTypes();
  }
}

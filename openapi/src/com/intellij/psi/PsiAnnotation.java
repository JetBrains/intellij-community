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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.meta.PsiMetaOwner;

/**
 * Represents a Java annotation.
 *
 * @author ven
 */
public interface PsiAnnotation extends PsiAnnotationMemberValue, PsiMetaOwner {
  /**
   * The empty array of PSI annotations which can be reused to avoid unnecessary allocations.
   */
  PsiAnnotation[] EMPTY_ARRAY = new PsiAnnotation[0];

  @NonNls String DEFAULT_REFERENCED_METHOD_NAME = "value";

  /**
   * Returns the list of parameters for the annotation.
   *
   * @return the parameter list instance.
   */
  @NotNull
  PsiAnnotationParameterList getParameterList();

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @Nullable @NonNls
  String getQualifiedName();

  /**
   * Returns the reference element representing the name of the annotation.
   *
   * @return the annotation name element.
   */
  @Nullable
  PsiJavaCodeReferenceElement getNameReferenceElement();

  /**
   * Returns the value of the annotation element with the specified name.
   *
   * @param attributeName name of the annotation element for which the value is requested.
   * @return the element value, or null if the annotation does not contain a value for
   * the element and the element has no default value.
   */
  @Nullable
  PsiAnnotationMemberValue findAttributeValue(String attributeName);
}

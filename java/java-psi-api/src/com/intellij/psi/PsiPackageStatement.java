/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@code package} Java statement.
 */
public interface PsiPackageStatement extends PsiElement {
  /**
   * Returns the Java code reference element specifying the declared name of the package.
   *
   * @return the element for the name of the package.
   */
  PsiJavaCodeReferenceElement getPackageReference();

  /**
   * Returns the declared name of the package; empty string for missing or malformed declaration
   *
   * @return the declared name of the package.
   */
  @NotNull String getPackageName();

  /**
   * Returns the list of annotations for the package.
   *
   * @return the modifier list containing the annotations applied to the package statement.
   */
  PsiModifierList getAnnotationList();
}

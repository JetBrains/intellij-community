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

import org.jetbrains.annotations.NotNull;

/**
 * Represents a list of Java classes referenced by the {@code implements}, {@code extends}, {@code throws}, or {@code with} clause.
 *
 * @see PsiClass#getExtendsList()
 * @see PsiClass#getImplementsList()
 * @see PsiMethod#getThrowsList()
 * @see PsiProvidesStatement#getImplementationList()
 */
public interface PsiReferenceList extends PsiElement {
  PsiReferenceList[] EMPTY_ARRAY = new PsiReferenceList[0];
  
  /**
   * Returns the array of reference elements contained in the list.
   */
  PsiJavaCodeReferenceElement @NotNull [] getReferenceElements();

  /**
   * Returns the array of classes referenced by elements in the list.
   */
  PsiClassType @NotNull [] getReferencedTypes();

  Role getRole();

  enum Role {
    THROWS_LIST,
    EXTENDS_LIST,
    IMPLEMENTS_LIST,
    PERMITS_LIST,
    EXTENDS_BOUNDS_LIST,
    PROVIDES_WITH_LIST
  }
}
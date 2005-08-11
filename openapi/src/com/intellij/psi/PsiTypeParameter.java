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

import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public interface PsiTypeParameter extends PsiClass {
  PsiTypeParameter[] EMPTY_ARRAY = new PsiTypeParameter[0];

  /**
   * Returns the extends list of the type parameter.
   * @return the extends list. For this particular kind of classes it never returns null.
   */
  @NotNull
  PsiReferenceList getExtendsList();

  @NotNull
  PsiTypeParameterListOwner getOwner ();

  int getIndex();
}

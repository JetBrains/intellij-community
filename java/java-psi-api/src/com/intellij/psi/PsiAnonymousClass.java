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
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java anonymous class.
 *
 * @see PsiNewExpression#getAnonymousClass() 
 */
public interface PsiAnonymousClass extends PsiClass {
  /**
   * Returns the reference element specifying the base class for the anonymous class.
   *
   * @return the reference element for the base class.
   */
  @NotNull
  PsiJavaCodeReferenceElement getBaseClassReference();

  /**
   * Returns the type for the base class of the anonymous class.
   *
   * @return the type for the base class.
   */
  @NotNull
  PsiClassType getBaseClassType();

  /**
   * Returns the list of arguments passed to the base class constructor.
   *
   * @return the argument list, or null if no argument list was specified.
   */
  @Nullable
  PsiExpressionList getArgumentList();

  boolean isInQualifiedNew();
}

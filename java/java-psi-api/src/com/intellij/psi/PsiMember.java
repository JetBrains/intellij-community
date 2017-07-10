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

import com.intellij.lang.jvm.JvmMember;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a member of a Java class (for example, a field or a method).
 */
public interface PsiMember extends PsiModifierListOwner, NavigatablePsiElement, JvmMember {
  /**
   * The empty array of PSI members which can be reused to avoid unnecessary allocations.
   */
  PsiMember[] EMPTY_ARRAY = new PsiMember[0];

  /**
   * Returns the class containing the member.
   *
   * @return the containing class.
   */
  @Nullable
  PsiClass getContainingClass();
}

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

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the list of modifiers and annotations on a Java element (class, method,
 * field and so on). Possible modifiers are defined as constants in the {@link PsiModifier} class.
 *
 * @see PsiModifierListOwner#getModifierList()
 */
public interface PsiModifierList extends PsiElement, PsiAnnotationOwner {
  /**
   * Checks if the modifier list has the specified modifier set either by an explicit keyword
   * or implicitly (for example, interface methods are implicitly public).
   *
   * @param name the name of the modifier to check.
   * @return true if the list has the modifier, false otherwise
   * @see #hasExplicitModifier(String)
   */
  boolean hasModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name);

  /**
   * Checks if the modifier list has the specified modifier set by an explicit keyword.
   *
   * @param name the name of the modifier to check.
   * @return true if the list has the modifier, false otherwise
   * @see #hasModifierProperty(String)
   */
  boolean hasExplicitModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String name);

  /**
   * Adds or removes the specified modifier to the modifier list.
   *
   * @param name  the name of the modifier to add or remove.
   * @param value true if the modifier should be added, false if it should be removed.
   * @throws IncorrectOperationException if the modification fails for some reason.
   */
  void setModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException;

  /**
   * Checks if it is possible to add or remove the specified modifier to the modifier list,
   * and throws an exception if the operation is not possible. Does not actually modify
   * anything.
   *
   * @param name  the name of the modifier to check the add or remove possibility for.
   * @param value true if the modifier should be added, false if it should be removed.
   * @throws IncorrectOperationException if the modification fails for some reason.
   */
  void checkSetModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException;

}

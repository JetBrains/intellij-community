/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * @author Medvedev Max
 */
public interface PsiTopLevelElementFactory {
  /**
   * Creates an empty class with the specified name.
   *
   * @param name the name of the class to create.
   * @return the created class instance.
   * @throws com.intellij.util.IncorrectOperationException
   *          if <code>name</code> is not a valid Java identifier.
   */
  @NotNull
  PsiClass createClass(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @param name the name of the interface to create.
   * @return the created interface instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @NotNull
  PsiClass createInterface(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @param name the name of the enum to create.
   * @return the created enum instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @NotNull
  PsiClass createEnum(@NotNull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @param name the name of the field to create.
   * @param type the type of the field to create.
   * @return the created field instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull
  PsiField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @param name       the name of the method to create.
   * @param returnType the return type of the method to create.
   * @return the created method instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull
  PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   *
   * @return the created constructor instance.
   */
  @NotNull
  PsiMethod createConstructor();

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @NotNull
  PsiClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type.
   *
   * @param name the name of the parameter to create.
   * @param type the type of the parameter to create.
   * @return the created parameter instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull
  PsiParameter createParameter(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @param names the array of names for the parameters to create.
   * @param types the array of types for the parameters to create.
   * @return the created parameter list.
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  @NotNull
  PsiParameterList createParameterList(@NotNull @NonNls String[] names, @NotNull PsiType[] types) throws IncorrectOperationException;

  PsiMethod createMethodFromText(String text, @Nullable PsiElement context);
}

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
 * Represents a constant in a Java enum type.
 *
 * @author dsl
 */
public interface PsiEnumConstant extends PsiField, PsiConstructorCall {
  /**
   * Returns the list of arguments passed to the constructor of the enum type to create the
   * instance of the constant.
   *
   * @return the list of arguments, or null
   */
  @Override
  @Nullable
  PsiExpressionList getArgumentList();

  /**
   * Returns the class body attached to the enum constant declaration.
   *
   * @return the enum constant class body, or null if
   * the enum constant does not have one.
   */
  @Nullable
  PsiEnumConstantInitializer getInitializingClass();

  @NotNull
  PsiEnumConstantInitializer getOrCreateInitializingClass();

}

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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java local variable.
 */
public interface PsiLocalVariable extends PsiVariable {
  /**
   * Adds initializer to the variable declaration statement or, if <code>initializer</code>
   * parameter is null, removes initializer from variable.
   *
   * @param initializer the initializer to add.
   * @throws IncorrectOperationException if the modifications fails for some reason.
   * @since 5.0.2
   */
  void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException;

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  PsiTypeElement getTypeElement();
}

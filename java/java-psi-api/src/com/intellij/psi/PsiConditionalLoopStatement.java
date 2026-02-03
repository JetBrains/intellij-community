/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * A loop statement that exits on condition.
 */
public interface PsiConditionalLoopStatement extends PsiLoopStatement {
  /**
   * Returns the expression representing the condition of the loop.
   * The condition is checked after every loop iteration, and
   * iteration stops when condition evaluates to {@code false}.
   * 
   * <p>
   *   Concrete loops may or may not check the condition before the first iteration.
   *   Also, additional steps could be performed at the loop start or between the body execution 
   *   and condition checking. 
   * </p>
   *
   * @return the expression, or null if condition is absent. The absent condition
   * could be a legal case or incomplete statement, depending on the kind of the loop.
   */
  @Contract(pure = true)
  @Nullable PsiExpression getCondition();
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author cdr
*/
public enum Nullness {
  NOT_NULL, NULLABLE,UNKNOWN;

  /**
   * Convert to boolean which is used to encode nullability in DfaFactType.CAN_BE_NULL
   *
   * @return TRUE if NULLABLE, FALSE if NOT_NULL, null if UNKNOWN
   */
  @Nullable
  public Boolean toBoolean() {
    return this == UNKNOWN ? null : this == NULLABLE;
  }

  /**
   * Convert from boolean fact which is used to encode nullability in DfaFactType.CAN_BE_NULL
   *
   * @param fact TRUE if NULLABLE, FALSE if NOT_NULL, null if UNKNOWN
   * @return the corresponding nullness value
   */
  @NotNull
  public static Nullness fromBoolean(@Nullable Boolean fact) {
    return fact == null ? UNKNOWN : fact ? NULLABLE : NOT_NULL;
  }
}

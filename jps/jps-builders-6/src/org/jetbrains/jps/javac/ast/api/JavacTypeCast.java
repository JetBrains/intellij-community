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
package org.jetbrains.jps.javac.ast.api;

import org.jetbrains.annotations.NotNull;

public class JavacTypeCast {
  @NotNull
  private final JavacRef.JavacClass myOperandType;
  @NotNull
  private final JavacRef.JavacClass myCastType;

  public JavacTypeCast(@NotNull JavacRef.JavacClass operandType, @NotNull JavacRef.JavacClass castType) {
    myOperandType = operandType;
    myCastType = castType;
  }

  @NotNull
  public JavacRef.JavacClass getOperandType() {
    return myOperandType;
  }

  @NotNull
  public JavacRef.JavacClass getCastType() {
    return myCastType;
  }
}

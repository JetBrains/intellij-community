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
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InitialPatternCondition<T> {
  private final Class<T> myAcceptedClass;

  protected InitialPatternCondition(@NotNull Class<T> aAcceptedClass) {
    myAcceptedClass = aAcceptedClass;
  }

  @NotNull
  public Class<T> getAcceptedClass() {
    return myAcceptedClass;
  }

  public boolean accepts(@Nullable Object o, final ProcessingContext context) {
    return myAcceptedClass.isInstance(o);
  }

  @NonNls
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(@NonNls @NotNull StringBuilder builder, final String indent) {
    builder.append("instanceOf(").append(myAcceptedClass.getSimpleName()).append(")");
  }
}

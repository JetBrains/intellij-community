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
package com.intellij.refactoring.extractMethod;

import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtractMethodSettingsImpl<T> implements ExtractMethodSettings<T> {
  @NotNull private final String myMethodName;
  private final AbstractVariableData @NotNull [] myVariableData;
  @Nullable private final T myVisibility;

  public ExtractMethodSettingsImpl(@NotNull String methodName,
                                   AbstractVariableData @NotNull [] abstractVariableData,
                                   @Nullable T visibility) {

    myMethodName = methodName;
    myVariableData = abstractVariableData;
    myVisibility = visibility;
  }

  @NotNull
  @Override
  public String getMethodName() {
    return myMethodName;
  }

  @Override
  public AbstractVariableData @NotNull [] getAbstractVariableData() {
    return myVariableData;
  }

  @Nullable
  @Override
  public T getVisibility() {
    return myVisibility;
  }
}

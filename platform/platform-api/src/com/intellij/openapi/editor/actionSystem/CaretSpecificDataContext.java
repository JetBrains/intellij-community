/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataContextWrapper;
import com.intellij.openapi.editor.Caret;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated Use {@link EditorActionHandler#caretDataContext(DataContext, Caret)} instead */
@Deprecated(forRemoval = true)
public final class CaretSpecificDataContext extends DataContextWrapper {
  private final Caret myCaret;

  public static @NotNull CaretSpecificDataContext create(@NotNull DataContext delegate, @NotNull Caret caret) {
    if (delegate instanceof CaretSpecificDataContext && CommonDataKeys.CARET.getData(delegate) == caret) {
      return (CaretSpecificDataContext)delegate;
    }
    return new CaretSpecificDataContext(delegate, caret);
  }

  private CaretSpecificDataContext(@NotNull DataContext delegate, @NotNull Caret caret) {
    super(delegate);
    myCaret = caret;
  }

  @Override
  public @Nullable Object getRawCustomData(@NotNull String dataId) {
    if (CommonDataKeys.CARET.is(dataId)) return myCaret;
    return null;
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public interface PlaceHolder extends PlaceProvider {
  void setPlace(@NonNls @NotNull String place);

  /**
   * @deprecated this method is temporary added to keep compatibility with code which was compiled against the old version of {@link PlaceHolder}
   * with generic parameter and therefore refers to the method with parameter type {@code Object} in its bytecode. This method isn't supposed to
   * be used directly.
   */
  @Deprecated
  default void setPlace(Object place) {
    setPlace((String)place);
  }
}

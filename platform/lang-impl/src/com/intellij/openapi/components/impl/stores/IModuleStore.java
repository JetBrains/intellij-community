/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface IModuleStore extends IComponentStore {
  void setModuleFilePath(@NotNull String filePath);

  @NotNull
  String getModuleFilePath();

  void setOption(@NotNull Key<String> key, @NotNull String value);

  void clearOption(@NotNull Key<String> key);

  String getOptionValue(@NotNull Key<String> key);
}

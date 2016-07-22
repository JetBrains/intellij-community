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
package com.intellij.ide.passwordSafe;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PasswordStorage {
  @Nullable
  default String getPassword(@NotNull String key) {
    return getPassword(null, null, key);
  }

  @Nullable
  String getPassword(@Nullable Project project, @Nullable Class requestor, @NotNull String key);

  /**
   * Remove password stored in a password safe
   *
   * @param requestor the requestor class
   * @param key       the key for the password
   */
  default void removePassword(@Nullable Project project, @Nullable Class requestor, String key) {
    storePassword(project, requestor, key, null);
  }

  default void setPassword(@NotNull String key, @Nullable String value) {
    storePassword(null, null, key, value);
  }

  void storePassword(@Nullable Project project, @Nullable Class requestor, @NotNull String key, @Nullable String value);
}

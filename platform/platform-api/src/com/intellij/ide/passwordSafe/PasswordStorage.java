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

import com.intellij.credentialStore.CredentialStore;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

public interface PasswordStorage extends CredentialStore {
  @Deprecated
  @Nullable
  default String getPassword(@NotNull Class<?> requestor, @NotNull String accountName) {
    return getPassword(CredentialAttributes(requestor, accountName));
  }

  @Deprecated
  default void setPassword(@NotNull Class<?> requestor, @NotNull String accountName, @Nullable String value) {
    set(CredentialAttributes(requestor, accountName), value == null ? null : new Credentials(accountName, value));
  }

  /**
   * @deprecated Please use {@link #setPassword} and pass value as null
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void removePassword(@SuppressWarnings("UnusedParameters") @Nullable Project project, @NotNull Class requestor, String key) {
    //noinspection deprecation
    setPassword(requestor, key, null);
  }

  /**
   * @deprecated Please use {@link #setPassword}
   */
  @Deprecated
  default void storePassword(@SuppressWarnings("UnusedParameters") @Nullable Project project, @NotNull Class requestor, @NotNull String key, @Nullable String value) {
    //noinspection deprecation
    setPassword(requestor, key, value);
  }

  @Deprecated
  @Nullable
  default String getPassword(@SuppressWarnings("UnusedParameters") @Nullable Project project, @NotNull Class requestor, @NotNull String key) {
    //noinspection deprecation
    return getPassword(requestor, key);
  }
}

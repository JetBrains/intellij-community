// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated use {@link com.intellij.credentialStore.CredentialStore} instead */
@Deprecated(forRemoval = true)
@SuppressWarnings("DeprecatedIsStillUsed")
public interface PasswordStorage {
  @Nullable Credentials get(@NotNull CredentialAttributes attributes);

  void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials);
}

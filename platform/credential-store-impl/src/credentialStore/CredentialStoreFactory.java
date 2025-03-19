// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public interface CredentialStoreFactory {
  ExtensionPointName<CredentialStoreFactory> CREDENTIAL_STORE_FACTORY = ExtensionPointName.create("com.intellij.credentialStore");

  @Nullable CredentialStore create();
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public interface CredentialStoreFactory {
  ExtensionPointName<CredentialStoreFactory> CREDENTIAL_STORE_FACTORY = ExtensionPointName.create("com.intellij.credentialStore");

  @Nullable
  CredentialStore create();
}

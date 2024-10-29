// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class CredentialsManagerImpl extends CredentialsManager {
  @Override
  public List<CredentialsType<?>> getAllTypes() {
    return CredentialsType.EP_NAME.getExtensionList();
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void loadCredentials(String interpreterPath, @Nullable Element element, RemoteSdkAdditionalData data) {
    for (CredentialsType type : CredentialsType.EP_NAME.getExtensionList()) {
      if (type.hasPrefix(interpreterPath)) {
        Object credentials = type.createCredentials();
        try {
          type.getHandler(credentials).load(element);
        }
        catch (CannotLoadCredentialsException e) {
          Logger.getInstance(CredentialsManagerImpl.class).warn(e);
          continue;
        }
        data.setCredentials(type.getCredentialsKey(), credentials);
        return;
      }
    }

    UnknownCredentialsHolder credentials = CredentialsType.UNKNOWN.createCredentials();
    credentials.setSdkId(interpreterPath);
    if (element != null) {
      credentials.load(element);
    }
    data.setCredentials(CredentialsType.UNKNOWN_CREDENTIALS, credentials);
  }
}

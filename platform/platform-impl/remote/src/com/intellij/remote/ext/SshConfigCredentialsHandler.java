// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.remote.SshConfigCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SshConfigCredentialsHandler extends RemoteCredentialsHandlerBase<SshConfigCredentialsHolder> {

  public SshConfigCredentialsHandler(SshConfigCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public @NotNull String getId() {
    return getCredentials().getCredentialsId();
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    return "(" + getCredentials().getCredentialsId() + interpreterPath + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    SshConfigCredentialsHolder credentials = getCredentials();
    if (rootElement != null) {
      credentials.load(rootElement);
    }
    else {
      credentials.cleanConfigData();
    }
  }
}

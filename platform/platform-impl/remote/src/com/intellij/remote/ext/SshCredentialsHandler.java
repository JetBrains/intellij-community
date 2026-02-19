// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.remote.RemoteCredentials;
import com.intellij.remote.RemoteCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SshCredentialsHandler extends RemoteCredentialsHandlerBase<RemoteCredentialsHolder> {

  public SshCredentialsHandler(RemoteCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public @NotNull String getId() {
    return constructSshCredentialsFullPath();
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public @NotNull String getPresentableDetails(String interpreterPath) {
    return "(" + constructSshCredentialsFullPath() + interpreterPath + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    if (rootElement != null) {
      getCredentials().load(rootElement);
    }
  }

  private @NotNull String constructSshCredentialsFullPath() {
    RemoteCredentials cred = getCredentials();
    return RemoteCredentialsHolder.SSH_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getLiteralPort();
  }
}

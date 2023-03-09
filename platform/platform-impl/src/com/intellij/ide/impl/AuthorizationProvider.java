// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface AuthorizationProvider {
  ExtensionPointName<AuthorizationProvider> EP_NAME = new ExtensionPointName<>("com.intellij.authorizationProvider");

  /**
   * This method is called from a background thread in order to obtain an authorization access token for the given resource URL.
   * The access token has to be valid for at least 10 seconds. The implementation should not throw an exception, it is supposed to return a valid authentication token value or null if there was any error or
   * there is no access token obtained for the given resource.
   * The implementation is not expected to initiate an interactive authorization process and react on `Thread#interrupt`
   *
   * @param url the URL denoting the resource for which the access token is required
   * @return a valid and not expired access token or null otherwise (the access token does not exist for the given resource, or there was an error obtaining the token)
   */
  @Nullable
  String getAccessToken(@NotNull String url);
}

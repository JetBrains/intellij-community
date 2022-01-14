// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Provides custom authentication headers to be used in http requests to marketplace and custom IJ plugin repositories.
 * <p>
 * Note that authentication flow and login management is not in the scope of this EP.<br>
 * It is the responsibility of the plugin author to make sure that the credentials are up-to-date and correct.<br>
 * <strong>NB!</strong>: If your authentication is stateful(headers may change over time) please notify via
 * {@link PluginRepositoryAuthListener#notifyAuthChanged()} so that the cached responses that may have failed due to the lack
 * of authentication are invalidated
 */
public interface PluginRepositoryAuthProvider {

  ExtensionPointName<PluginRepositoryAuthProvider> EP_NAME = ExtensionPointName.create("com.intellij.pluginRepositoryAuthProvider");

  /**
   * Provide extra authentication headers(e.g. "Authentication: Bearer XXXXXX") based on the URL of the outgoing request.
   *
   * @return {@link Collections#emptyMap()} if url should not be handled or no credentials are available
   */
  @NotNull Map<String, String> getAuthHeaders(String url);


  boolean canHandle(String url);
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth


internal class
EnvironmentVariablePluginRepositoryAuthProvider : PluginRepositoryAuthProvider {
  val repositoryUrl: String? = System.getProperty("idea.plugins.host")
  private val hasRepositoryAuth = System.getenv("IDEA_PLUGINS_HOST_AUTH") != null

  override fun getAuthHeaders(url: String): Map<String, String> {
    if (!canHandle(url)) return emptyMap()
    return mapOf("Authorization" to System.getenv("IDEA_PLUGINS_HOST_AUTH"))
  }

  override fun canHandle(url: String): Boolean {
    return repositoryUrl != null && url.startsWith(repositoryUrl) && hasRepositoryAuth
  }
}
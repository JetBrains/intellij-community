// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager

/**
 * Extension point that provides credentials for Maven repositories that require authorization
 */
@ApiStatus.Experimental
interface JarRepositoryAuthenticationDataProvider {
  /**
   * @param url url of Maven repository
   * @return credentials that allow downloading libraries from the Maven repository located in [url].
   *         [null] if authorization is not needed.
   */
  fun provideAuthenticationData(url: String): AuthenticationData?

  data class AuthenticationData(val userName: String, val password: String)

  companion object {
    @JvmField
    val KEY = ExtensionPointName<JarRepositoryAuthenticationDataProvider>(
      "com.intellij.jarRepositoryAuthenticationDataProvider"
    )
  }
}

@RequiresBackgroundThread
internal fun obtainAuthenticationData(url: String): ArtifactRepositoryManager.ArtifactAuthenticationData? {
  for (extension in JarRepositoryAuthenticationDataProvider.KEY.extensions()) {
    val authData = extension.provideAuthenticationData(url)
    if (authData != null) {
      return ArtifactRepositoryManager.ArtifactAuthenticationData(authData.userName, authData.password)
    }
  }
  return null
}
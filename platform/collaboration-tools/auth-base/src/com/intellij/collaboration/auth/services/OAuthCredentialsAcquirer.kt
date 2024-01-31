// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

interface OAuthCredentialsAcquirer<T : Credentials> {
  /**
   * Exchange code for credentials
   */
  @RequiresBackgroundThread
  fun acquireCredentials(code: String): AcquireCredentialsResult<T>

  sealed class AcquireCredentialsResult<T : Credentials> {
    class Success<T : Credentials>(val credentials: T) : AcquireCredentialsResult<T>()

    class Error<T : Credentials>(val description: String) : AcquireCredentialsResult<T>()
  }
}
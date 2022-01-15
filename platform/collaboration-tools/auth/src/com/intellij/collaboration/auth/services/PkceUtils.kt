// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.util.io.DigestUtil
import java.util.*

object PkceUtils {
  fun generateCodeVerifier(): String = DigestUtil.randomToken()

  fun generateShaCodeChallenge(codeVerifier: String, withoutPadding: Boolean): String {
    val sha = DigestUtil.sha256().digest(codeVerifier.toByteArray())
    val encoder =
      if (withoutPadding) {
        Base64.getEncoder().withoutPadding()
      }
      else {
        Base64.getEncoder()
      }
    return encoder.encodeToString(sha)
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls

interface AccountDetails {
  @get:NlsSafe
  val name: String

  @get:NonNls
  val avatarUrl: String?
}
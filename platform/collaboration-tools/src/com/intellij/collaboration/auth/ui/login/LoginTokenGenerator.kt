// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

interface LoginTokenGenerator {

  fun canGenerateToken(serverUri: String): Boolean

  fun generateToken(serverUri: String)
}
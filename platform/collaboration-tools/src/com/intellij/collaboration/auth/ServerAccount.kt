// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.collaboration.api.ServerPath

/**
 * Base class for an account which correspond to a certain server
 * Most systems can have multiple servers while some have only the central one
 *
 * @property server some definition of a server, which can be presented to a user
 */
abstract class ServerAccount : Account() {
  abstract val server: ServerPath
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.schmizz.sshj

import net.schmizz.sshj.connection.channel.direct.PatchedExecChannel
import net.schmizz.sshj.connection.channel.direct.Session

/**
 * Subclass of SSHJ [SSHClient] that uses [PatchedSSHClient] which handles some strange features.
 */
internal class PatchedSSHClient(config: Config) : SSHClient(config) {
  override fun startSession(): Session {
    check(isConnected) { "Not connected" }
    check(isAuthenticated) { "Not authenticated" }
    val sess = PatchedExecChannel(this)
    sess.open()
    return sess
  }
}
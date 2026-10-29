// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus

/**
 * Logs the stderr lines of the IJent process that are not in the IJent log format.
 *
 * The transport usually writes these lines. An example is the OpenSSH client.
 * A deployer can send them to the log category of its transport.
 * The level of a line comes from its OpenSSH level tag: `debug1` is debug, `debug2` and `debug3` are trace.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.ijent.ssh.daemon.DaemonSshTransportSupervisor",
  "com.intellij.platform.ijent.ssh.daemon.OpenSshStderrLog",
  "com.intellij.platform.ijent.ssh.daemon.SshDeployingStrategy",
)
interface IjentForeignStderrLog {
  fun trace(message: String)
  fun debug(message: String)
  fun info(message: String)
  fun warn(message: String)
  fun error(message: String)

  /** The mediator calls it once, after the last line of the stderr. */
  fun stderrEnded() {}

  /** Writes the lines to [IjentLogger.LIFETIME_LOG], and the lines with no level tag to [IjentLogger.OTHER_LOG]. */
  object Default : IjentForeignStderrLog {
    override fun trace(message: String): Unit = IjentLogger.LIFETIME_LOG.trace(message)
    override fun debug(message: String): Unit = IjentLogger.LIFETIME_LOG.debug(message)
    override fun info(message: String): Unit = IjentLogger.OTHER_LOG.info(message)
    override fun warn(message: String): Unit = IjentLogger.LIFETIME_LOG.warn(message)
    override fun error(message: String): Unit = IjentLogger.LIFETIME_LOG.error(message)
  }
}
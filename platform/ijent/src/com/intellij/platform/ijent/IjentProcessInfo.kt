// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelApi

/**
 * [architecture] is the remote architecture of the built binary. Intended to be used for debugging purposes.
 * [remotePid] is a process ID of IJent running on the remote machine.
 * [version] is the version of the IJent binary. Intended to be used for debugging purposes.
 * [remoteExecutablePath] is the path to the IJent binary on the remote machine.
 */
interface IjentProcessInfo {
  val architecture: String
  val remotePid: EelApi.Pid
  val version: String
  val remoteExecutablePath: String
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

/**
 * [architecture] is the remote architecture of the built binary. Intended to be used for debugging purposes.
 * [remotePid] is a process ID of IJent running on the remote machine.
 * [version] is the version of the IJent binary. Intended to be used for debugging purposes.
 */
interface IjentInfo {
  val architecture: String
  val remotePid: IjentApi.Pid
  val version: String
}
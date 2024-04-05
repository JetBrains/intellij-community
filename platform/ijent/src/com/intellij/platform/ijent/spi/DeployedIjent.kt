// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentWindowsApi

interface DeployedIjent {
  val ijentApi: IjentApi

  val remotePathToBinary: String  // TODO Use IjentPath.Absolute.

  interface Posix : DeployedIjent {
    override val ijentApi: IjentPosixApi
  }

  interface Windows : DeployedIjent {
    override val ijentApi: IjentWindowsApi
  }
}
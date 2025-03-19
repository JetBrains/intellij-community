// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.eel.EelApi
import com.intellij.platform.ijent.IjentProcessInfo

data class IjentProcessInfoImpl(
  override val architecture: String,
  override val remotePid: EelApi.Pid,
  override val version: String,
) : IjentProcessInfo
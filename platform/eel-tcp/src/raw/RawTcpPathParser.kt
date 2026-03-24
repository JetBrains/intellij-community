// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.tcp.TcpEelDescriptor
import com.intellij.platform.eel.tcp.TcpEelPathParser

class RawTcpPathParser : TcpEelPathParser {
  override fun isInternalNameCompatible(internalName: String): Boolean {
    return internalName.startsWith(RawTcpConsts.SCHEME)
  }

  override fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor? {
    val deploy = RawTcpConsts.extractTcpDeploy(internalName) ?: return null
    return RawTcpEelDescriptor(deploy, osFamily)
  }
}
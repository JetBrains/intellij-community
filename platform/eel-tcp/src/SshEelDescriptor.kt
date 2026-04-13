// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelOsFamily
import org.jetbrains.annotations.NonNls

// TODO To be merged with com.intellij.platform.ijent.ssh.impl.SshEelDescriptor
class SshEelDescriptor(val sshConfigId: String, osFamily: EelOsFamily) : TcpEelDescriptor(osFamily) {
  override val rootPathString: String = "${SshEelConsts.pathFromSshId(sshConfigId, osFamily)}"
  override val name: @NonNls String = "Ssh Eel (id=$sshConfigId)"
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.util.annotations.VisibleToClasses
import java.nio.file.Path
import kotlin.io.path.Path

@VisibleToClasses(
  "com.intellij.platform.frontend.split.baseline.eel.RdEelFileChooserContributor",
)
object SshEelConsts {
  const val SCHEME: String = "ssh"

  fun internalName(sshConfigId: String): String = "$SCHEME-$sshConfigId"

  fun osFamilyFromPathSegment(pathSegment: String): EelOsFamily {
    val osSuffix = pathSegment.substringAfterLast("-")
    return EelOsFamily.entries.firstOrNull {
      osSuffix == it.name.lowercase()
    } ?: EelOsFamily.Posix
  }

  fun internalNameFromPathSegment(pathSegment: String): String {
    return pathSegment.removeSuffix("-posix").removeSuffix("-windows")
  }

  fun extractSshConfigId(internalName: String): String? {
    if (!internalName.startsWith("$SCHEME-")) return null
    val configId = internalName.removePrefix("$SCHEME-")
    return configId.ifEmpty { null }
  }

  fun pathFromSshId(sshId: String, osFamily: EelOsFamily): Path =
    Path("${TcpEelConstants.TCP_PATH_PREFIX}$SCHEME-$sshId-${osFamily.name.lowercase()}")
}
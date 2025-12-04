// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptorWithoutNativeFileChooserSupport
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import java.nio.file.Path

abstract class TcpEelDescriptor : EelDescriptorWithoutNativeFileChooserSupport, EelPathBoundDescriptor {
  abstract val rootPathString: String
  override val rootPath: Path
    get() = Path.of(rootPathString)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TcpEelDescriptor
    return rootPathString == other.rootPathString
  }

  override fun hashCode(): Int = rootPathString.hashCode()

  override val osFamily: EelOsFamily = EelOsFamily.Posix // FIXME
}
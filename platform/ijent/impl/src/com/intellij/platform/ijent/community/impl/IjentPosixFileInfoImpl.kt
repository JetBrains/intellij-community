// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.fs.IjentFileInfo
import com.intellij.platform.ijent.fs.IjentPath
import com.intellij.platform.ijent.fs.IjentPosixFileInfo
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
data class IjentPosixFileInfoImpl(
  override val path: IjentPath.Absolute,
  override val type: IjentFileInfo.Type,
) : IjentPosixFileInfo {
  data object Directory : IjentFileInfo.Type.Directory
  data object Regular : IjentFileInfo.Type.Regular
  data class SymlinkResolved(override val result: IjentPath.Absolute) : IjentPosixFileInfo.Type.Symlink.Resolved
  data object SymlinkUnresolved : IjentPosixFileInfo.Type.Symlink.Unresolved
  data object Other : IjentFileInfo.Type.Other
}
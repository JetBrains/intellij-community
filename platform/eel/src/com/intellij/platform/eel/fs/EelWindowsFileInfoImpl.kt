// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus
import java.time.ZonedDateTime

@ApiStatus.Internal
data class EelWindowsFileInfoImpl(
  override val type: EelFileInfo.Type,
  override val permissions: EelWindowsFileInfo.Permissions,
  override val creationTime: ZonedDateTime?,
  override val lastModifiedTime: ZonedDateTime?,
  override val lastAccessTime: ZonedDateTime?,
  override val volumeSerialNumber: Int,
  override val fileIndexHigh: Int,
  override val fileIndexLow: Int,
) : EelWindowsFileInfo {
  data class Permissions(
    override val isReadOnly: Boolean,
    override val isHidden: Boolean,
    override val isArchive: Boolean,
    override val isSystem: Boolean
  ) : EelWindowsFileInfo.Permissions {
    constructor(eelPermissions: EelWindowsFileInfo.Permissions) : this(
      isReadOnly = eelPermissions.isReadOnly,
      isHidden = eelPermissions.isHidden,
      isArchive = eelPermissions.isArchive,
      isSystem = eelPermissions.isSystem,
    )
  }
}
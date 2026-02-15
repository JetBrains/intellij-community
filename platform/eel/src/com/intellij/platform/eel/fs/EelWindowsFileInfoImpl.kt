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
) : EelWindowsFileInfo {
  data class Permissions(
    override val isReadOnly: Boolean,
    override val isHidden: Boolean,
    override val isArchive: Boolean,
    override val isSystem: Boolean
  ) : EelWindowsFileInfo.Permissions
}
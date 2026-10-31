// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Stores the times as epoch nanoseconds plus one zone, see [EelFileTimes]. The [ZonedDateTime] getters build a new value on each call.
 *
 * The `Long`s save memory: a remote tree prefetch keeps millions of infos (IJPL-260315). Three [ZonedDateTime] with their
 * `LocalDateTime`, `LocalDate` and `LocalTime` cost ~250 B per info; the `Long`s cut a Posix info from 352 to 120 B (measured on 500K infos).
 */
@ApiStatus.Internal
data class EelWindowsFileInfoImpl(
  override val type: EelFileInfo.Type,
  override val permissions: EelWindowsFileInfo.Permissions,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val creationTimeNanos: Long,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val lastModifiedTimeNanos: Long,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val lastAccessTimeNanos: Long,
  /** The zone of all three times. */
  val timeZone: ZoneId,
  override val volumeSerialNumber: Int,
  override val fileIndexHigh: Int,
  override val fileIndexLow: Int,
) : EelWindowsFileInfo {
  /** Keeps the instant of each time. The zone is the one of the first non-null time, see [EelFileTimes.zoneOf]. */
  constructor(
    type: EelFileInfo.Type,
    permissions: EelWindowsFileInfo.Permissions,
    creationTime: ZonedDateTime?,
    lastModifiedTime: ZonedDateTime?,
    lastAccessTime: ZonedDateTime?,
    volumeSerialNumber: Int,
    fileIndexHigh: Int,
    fileIndexLow: Int,
  ) : this(
    type = type,
    permissions = permissions,
    creationTimeNanos = EelFileTimes.epochNanos(creationTime),
    lastModifiedTimeNanos = EelFileTimes.epochNanos(lastModifiedTime),
    lastAccessTimeNanos = EelFileTimes.epochNanos(lastAccessTime),
    timeZone = EelFileTimes.zoneOf(lastModifiedTime, creationTime, lastAccessTime),
    volumeSerialNumber = volumeSerialNumber,
    fileIndexHigh = fileIndexHigh,
    fileIndexLow = fileIndexLow,
  )

  override val creationTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(creationTimeNanos, timeZone)
  override val lastModifiedTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(lastModifiedTimeNanos, timeZone)
  override val lastAccessTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(lastAccessTimeNanos, timeZone)

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
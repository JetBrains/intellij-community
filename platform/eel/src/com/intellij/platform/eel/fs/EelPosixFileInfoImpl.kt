// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Stores the times as epoch nanoseconds plus one zone, see [EelFileTimes]. The [ZonedDateTime] getters build a new value on each call.
 *
 * The `Long`s save memory: a remote tree prefetch keeps millions of infos (IJPL-260315). Three [ZonedDateTime] with their
 * `LocalDateTime`, `LocalDate` and `LocalTime` cost ~250 B per info; the `Long`s cut a Posix info from 352 to 120 B (measured on 500K infos).
 */
@ApiStatus.Internal
data class EelPosixFileInfoImpl(
  override val type: EelPosixFileInfo.Type,
  override val permissions: EelPosixFileInfo.Permissions,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val creationTimeNanos: Long,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val lastModifiedTimeNanos: Long,
  /** Epoch nanoseconds, or [EelFileTimes.NONE]. */
  val lastAccessTimeNanos: Long,
  /** The zone of all three times. */
  val timeZone: ZoneId,
  override val inodeDev: Long,
  override val inodeIno: Long,
) : EelPosixFileInfo {
  /** Keeps the instant of each time. The zone is the one of the first non-null time, see [EelFileTimes.zoneOf]. */
  constructor(
    type: EelPosixFileInfo.Type,
    permissions: EelPosixFileInfo.Permissions,
    creationTime: ZonedDateTime?,
    lastModifiedTime: ZonedDateTime?,
    lastAccessTime: ZonedDateTime?,
    inodeDev: Long,
    inodeIno: Long,
  ) : this(
    type = type,
    permissions = permissions,
    creationTimeNanos = EelFileTimes.epochNanos(creationTime),
    lastModifiedTimeNanos = EelFileTimes.epochNanos(lastModifiedTime),
    lastAccessTimeNanos = EelFileTimes.epochNanos(lastAccessTime),
    timeZone = EelFileTimes.zoneOf(lastModifiedTime, creationTime, lastAccessTime),
    inodeDev = inodeDev,
    inodeIno = inodeIno,
  )

  override val creationTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(creationTimeNanos, timeZone)
  override val lastModifiedTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(lastModifiedTimeNanos, timeZone)
  override val lastAccessTime: ZonedDateTime? get() = EelFileTimes.toZonedDateTime(lastAccessTimeNanos, timeZone)

  data class Directory(override val sensitivity: EelFileInfo.CaseSensitivity) : EelFileInfo.Type.Directory
  data class Regular(override val size: Long) : EelFileInfo.Type.Regular
  data class SymlinkResolvedAbsolute(override val result: EelPath) : EelPosixFileInfo.Type.Symlink.Resolved.Absolute
  data class SymlinkResolvedRelative(override val result: String) : EelPosixFileInfo.Type.Symlink.Resolved.Relative
  data object SymlinkUnresolved : EelPosixFileInfo.Type.Symlink.Unresolved
  data object Other : EelFileInfo.Type.Other

  data class Permissions(
    override val owner: Int,
    override val group: Int,
    val mask: Int,
  ) : EelPosixFileInfo.Permissions {
    override val otherCanExecute: Boolean get() = (mask and 0x1) != 0 // (00001) execute/search by others
    override val otherCanWrite: Boolean get() = (mask and 0x2) != 0 // (00002) write by others
    override val otherCanRead: Boolean get() = (mask and 0x4) != 0 // (00004) read by others
    override val groupCanExecute: Boolean get() = (mask and 0x8) != 0 // (00010) execute/search by group
    override val groupCanWrite: Boolean get() = (mask and 0x10) != 0 // (00020) write by group
    override val groupCanRead: Boolean get() = (mask and 0x20) != 0 // (00040) read by group
    override val ownerCanExecute: Boolean get() = (mask and 0x40) != 0 // (00100) execute/search by owner
    override val ownerCanWrite: Boolean get() = (mask and 0x80) != 0 // (00200) write by owner
    override val ownerCanRead: Boolean get() = (mask and 0x100) != 0 // (00400) read by owner
    override val stickyBit: Boolean get() = (mask and 0x200) != 0 // (01000) sticky bit
    override val setGid: Boolean get() = (mask and 0x400) != 0 // (02000) set-group-ID
    override val setUid: Boolean get() = (mask and 0x800) != 0 // (04000) set-user-ID
  }
}

/**
 * Compact times for [EelPosixFileInfoImpl] and [EelWindowsFileInfoImpl]: a [ZonedDateTime] with its `LocalDateTime`, `LocalDate`
 * and `LocalTime` costs ~80 B, a `Long` costs 8 B. A remote prefetch keeps millions of infos (IJPL-260315).
 *
 * A time is a `Long` of epoch nanoseconds, [NONE] for an absent time. That covers the years 1677..2262; [epochNanos] saturates
 * a time outside that range to the nearest end instead of throwing.
 */
@ApiStatus.Internal
object EelFileTimes {
  /** An absent time. */
  const val NONE: Long = Long.MIN_VALUE

  /** The smallest stored time, one above [NONE]. */
  const val MIN_EPOCH_NANOS: Long = Long.MIN_VALUE + 1

  /** The zone of IJent times. One shared instance; `ZoneId.of("UTC")` makes a new one on each call. */
  @JvmField
  val UTC: ZoneId = ZoneId.of("UTC")

  private const val NANOS_PER_SECOND: Long = 1_000_000_000L

  /** [nanos] is the nanosecond of the second, `0..999_999_999`. Saturates to [MIN_EPOCH_NANOS]..[Long.MAX_VALUE]. */
  fun epochNanos(seconds: Long, nanos: Long): Long {
    val result = try {
      Math.addExact(Math.multiplyExact(seconds, NANOS_PER_SECOND), nanos)
    }
    catch (_: ArithmeticException) {
      if (seconds < 0) MIN_EPOCH_NANOS else Long.MAX_VALUE
    }
    return maxOf(result, MIN_EPOCH_NANOS)
  }

  fun epochNanos(time: ZonedDateTime?): Long {
    if (time == null) return NONE
    val instant = time.toInstant()
    return epochNanos(instant.epochSecond, instant.nano.toLong())
  }

  fun toZonedDateTime(epochNanos: Long, zone: ZoneId): ZonedDateTime? {
    if (epochNanos == NONE) return null
    val instant = Instant.ofEpochSecond(Math.floorDiv(epochNanos, NANOS_PER_SECOND), Math.floorMod(epochNanos, NANOS_PER_SECOND))
    return ZonedDateTime.ofInstant(instant, zone)
  }

  /** The zone of the first non-null time, [UTC] if all are null. */
  fun zoneOf(first: ZonedDateTime?, second: ZonedDateTime?, third: ZonedDateTime?): ZoneId =
    (first ?: second ?: third)?.zone ?: UTC
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.fs.EelFileSystemApi.*
import com.intellij.platform.eel.path.EelPath

internal data class WriteOptionsImpl2(
  override val path: EelPath,
  override var append: Boolean = false,
  override var truncateExisting: Boolean = false,
  override var creationMode: FileWriterCreationMode = FileWriterCreationMode.ONLY_OPEN_EXISTING,
) : WriteOptions, WriteOptions.Builder {
  override fun append(v: Boolean): WriteOptions.Builder = apply { append = v }

  override fun truncateExisting(v: Boolean): WriteOptions.Builder = apply { truncateExisting = v }

  override fun creationMode(v: FileWriterCreationMode): WriteOptions.Builder = apply { creationMode = v }

  override fun build(): WriteOptions {
    return copy()
  }
}

internal data class CopyOptionsImpl2(
  override val source: EelPath,
  override val target: EelPath,
  override var copyRecursively: Boolean = false,
  override var replaceExisting: Boolean = false,
  override var preserveAttributes: Boolean = false,
  override var interruptible: Boolean = false,
  override var followLinks: Boolean = false,
) : CopyOptions, CopyOptions.Builder {
  override fun copyRecursively(v: Boolean): CopyOptions.Builder = apply { copyRecursively = v }

  override fun replaceExisting(v: Boolean): CopyOptions.Builder = apply { replaceExisting = v }

  override fun preserveAttributes(v: Boolean): CopyOptions.Builder = apply { preserveAttributes = v }

  override fun interruptible(v: Boolean): CopyOptions.Builder = apply { interruptible = v }

  override fun followLinks(v: Boolean): CopyOptions.Builder = apply { followLinks = v }

  override fun build(): CopyOptions {
    return copy()
  }
}

internal data class TimeSinceEpochImpl(override val seconds: ULong, override val nanoseconds: UInt) : TimeSinceEpoch

internal data class ChangeAttributesOptionsImpl2(
  override var accessTime: TimeSinceEpoch? = null,
  override var modificationTime: TimeSinceEpoch? = null,
  override var permissions: EelFileInfo.Permissions? = null,
) : ChangeAttributesOptions, ChangeAttributesOptions.Builder {
  override val path: EelPath
    get() = TODO("Not yet implemented")

  override fun accessTime(duration: TimeSinceEpoch): ChangeAttributesOptions.Builder = apply { accessTime = duration }

  override fun modificationTime(duration: TimeSinceEpoch): ChangeAttributesOptions.Builder = apply { modificationTime = duration }

  override fun permissions(permissions: EelFileInfo.Permissions): ChangeAttributesOptions.Builder = apply { this.permissions = permissions }

  override fun build(): ChangeAttributesOptions {
    return copy()
  }
}

internal data class CreateTemporaryEntryOptionsImpl2(
  override var prefix: String = "tmp",
  override var suffix: String = "",
  override var deleteOnExit: Boolean = false,
  override var parentDirectory: EelPath? = null,
) : CreateTemporaryEntryOptions, CreateTemporaryEntryOptions.Builder {
  override fun prefix(prefix: String): CreateTemporaryEntryOptions.Builder = apply {
    this.prefix = prefix
  }

  override fun suffix(suffix: String): CreateTemporaryEntryOptions.Builder = apply {
    this.suffix = suffix
  }

  override fun deleteOnExit(deleteOnExit: Boolean): CreateTemporaryEntryOptions.Builder = apply {
    this.deleteOnExit = deleteOnExit
  }

  override fun parentDirectory(parentDirectory: EelPath?): CreateTemporaryEntryOptions.Builder = apply {
    this.parentDirectory = parentDirectory
  }

  override fun build(): CreateTemporaryEntryOptions {
    return copy()
  }
}

internal data class AbsoluteSymbolicLinkTarget(override val path: EelPath) : EelFileSystemPosixApi.SymbolicLinkTarget.Absolute
internal data class RelativeSymbolicLinkTarget(override val reference: List<String>) : EelFileSystemPosixApi.SymbolicLinkTarget.Relative
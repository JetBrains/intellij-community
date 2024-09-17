// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.fs.EelFileSystemApi.CopyOptions
import com.intellij.platform.eel.fs.EelFileSystemApi.FileWriterCreationMode
import com.intellij.platform.eel.path.EelPath

internal data class WriteOptionsImpl(
  override val path: EelPath.Absolute,
  override var append: Boolean = false,
  override var truncateExisting: Boolean = false,
  override var creationMode: FileWriterCreationMode = FileWriterCreationMode.ONLY_OPEN_EXISTING,
) : EelFileSystemApi.WriteOptions {
  override fun append(v: Boolean): EelFileSystemApi.WriteOptions = apply { append = v }

  override fun truncateExisting(v: Boolean): EelFileSystemApi.WriteOptions = apply { truncateExisting = v }

  override fun creationMode(v: FileWriterCreationMode): EelFileSystemApi.WriteOptions = apply { creationMode = v }
}

internal data class CopyOptionsImpl(
  override val source: EelPath.Absolute,
  override val target: EelPath.Absolute,
  override var copyRecursively: Boolean = false,
  override var replaceExisting: Boolean = false,
  override var preserveAttributes: Boolean = false,
  override var interruptible: Boolean = false,
  override var followLinks: Boolean = false,
) : CopyOptions {
  override fun copyRecursively(v: Boolean): CopyOptions = apply { copyRecursively = v }

  override fun replaceExisting(v: Boolean): CopyOptions = apply { replaceExisting = v }

  override fun preserveAttributes(v: Boolean): CopyOptions = apply { preserveAttributes = v }

  override fun interruptible(v: Boolean): CopyOptions = apply { interruptible = v }

  override fun followLinks(v: Boolean): CopyOptions = apply { followLinks = v }
}
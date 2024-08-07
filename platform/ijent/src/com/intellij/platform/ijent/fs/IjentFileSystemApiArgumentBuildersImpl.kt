// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.fs.IjentFileSystemApi.CopyOptions
import com.intellij.platform.ijent.fs.IjentFileSystemApi.FileWriterCreationMode

internal data class WriteOptionsImpl internal constructor(
  override val path: IjentPath.Absolute,
  override var append: Boolean = false,
  override var truncateExisting: Boolean = false,
  override var creationMode: FileWriterCreationMode = FileWriterCreationMode.ONLY_OPEN_EXISTING,
) : IjentFileSystemApi.WriteOptions {
  override fun append(v: Boolean): IjentFileSystemApi.WriteOptions = apply { append = v }

  override fun truncateExisting(v: Boolean): IjentFileSystemApi.WriteOptions = apply { truncateExisting = v }

  override fun creationMode(v: FileWriterCreationMode): IjentFileSystemApi.WriteOptions = apply { creationMode = v }
}

internal data class CopyOptionsImpl internal constructor(
  override val source: IjentPath.Absolute,
  override val target: IjentPath.Absolute,
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
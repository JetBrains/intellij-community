// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
import com.intellij.platform.ijent.fs.IjentFileSystemApi.*
import com.intellij.util.text.nullize
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.*

/**
 * Returns an adapter from [IjentFileSystemApi] to [java.nio.file.FileSystem]. The adapter is automatically registered in advance,
 * also it is automatically closed when it is needed.
 *
 * The function is idempotent and thread-safe.
 */
fun IjentFileSystemApi.asNioFileSystem(): FileSystem {
  val nioFsProvider = IjentNioFileSystemProvider.getInstance()
  val uri = URI(nioFsProvider.scheme, id.id, null, null)
  return try {
    nioFsProvider.getFileSystem(uri)
  }
  catch (ignored: FileSystemNotFoundException) {
    try {
      nioFsProvider.newFileSystem(uri, mutableMapOf<String, Any>())
    }
    catch (ignored: FileSystemAlreadyExistsException) {
      nioFsProvider.getFileSystem(uri)
    }
  }
}

@Throws(FileSystemException::class)
internal fun IjentFsResult.Error.throwFileSystemException(): Nothing {
  throw when (this) {
    is IjentFsResult.DoesNotExist, is IjentFsResult.NotFile -> NoSuchFileException(where.toString(), null, message.nullize())
    is IjentFsResult.NotDirectory -> NotDirectoryException(where.toString())
    is IjentFsResult.PermissionDenied -> AccessDeniedException(where.toString(), null, message.nullize())
    is IjentOpenedFile.Seek.InvalidValue -> TODO()
  }
}

internal fun Path.toIjentPath(isWindows: Boolean): IjentPath =
  when {
    this is IjentNioPath -> ijentPath

    startsWith("ijent:") && nameCount >= 3 ->
      IjentNioFileSystemProvider.getInstance()
        .getPath(URI(asSequence().drop(1).joinToString("/", prefix = "ijent://") {
          URLEncoder.encode(it.toString(), StandardCharsets.UTF_8)
        }))
        .ijentPath

    isAbsolute -> throw InvalidPathException(toString(), "This path can't be converted to IjentPath")

    else -> IjentPath.Relative.parse(toString()).getOrThrow()
  }
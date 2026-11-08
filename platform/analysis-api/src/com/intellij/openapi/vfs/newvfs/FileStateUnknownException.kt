// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * The file system cannot tell the state of the file now: the file can exist or not.
 * It is not "absent", so the VFS must not delete the file or mark its directory as fully loaded because of it.
 *
 * @see com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem
 * @see FileSystemUnavailableException
 */
@ApiStatus.Internal
open class FileStateUnknownException(cause: Throwable) : IOException(cause)

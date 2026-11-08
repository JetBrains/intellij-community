// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import org.jetbrains.annotations.ApiStatus

/**
 * The file system cannot answer now, because the environment behind the path is not connected => state of the file is unknown.
 *
 * @see com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem
 */
@ApiStatus.Internal
class FileSystemUnavailableException(cause: Throwable) : FileStateUnknownException(cause)

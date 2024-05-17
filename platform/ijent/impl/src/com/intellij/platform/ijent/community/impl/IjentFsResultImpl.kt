// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.fs.*

@Suppress("unused") // Usages are to be implemented later.
object IjentFsResultImpl {
  data class Ok<T, E : IjentFsError>(override val value: T) : IjentFsResult.Ok<T, E>
  data class Error<T, E : IjentFsError>(override val error: E) : IjentFsResult.Error<T, E>

  data class DoesNotExist(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.FileReaderError.DoesNotExist,
    IjentFileSystemApi.FileWriterError.DoesNotExist,
    IjentFileSystemApi.ListDirectoryError.DoesNotExist,
    IjentFileSystemApi.SameFileError.DoesNotExist,
    IjentFileSystemApi.StatError.DoesNotExist,
    IjentOpenedFile.Reader.ReadError.DoesNotExist,
    IjentOpenedFile.SeekError.DoesNotExist,
    IjentOpenedFile.Writer.WriteError.DoesNotExist,
    IjentFileSystemApi.CanonicalizeError.DoesNotExist

  class PermissionDenied(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.PermissionDenied,
    IjentFileSystemApi.FileReaderError.PermissionDenied,
    IjentFileSystemApi.FileWriterError.PermissionDenied,
    IjentFileSystemApi.ListDirectoryError.PermissionDenied,
    IjentFileSystemApi.SameFileError.PermissionDenied,
    IjentFileSystemApi.StatError.PermissionDenied,
    IjentOpenedFile.Reader.ReadError.PermissionDenied,
    IjentOpenedFile.SeekError.PermissionDenied,
    IjentOpenedFile.Writer.WriteError.PermissionDenied

  data class NotDirectory(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.NotDirectory,
    IjentFileSystemApi.FileReaderError.NotDirectory,
    IjentFileSystemApi.FileWriterError.NotDirectory,
    IjentFileSystemApi.ListDirectoryError.NotDirectory,
    IjentFileSystemApi.SameFileError.NotDirectory,
    IjentFileSystemApi.StatError.NotDirectory,
    IjentOpenedFile.Reader.ReadError.NotDirectory,
    IjentOpenedFile.SeekError.NotDirectory,
    IjentOpenedFile.Writer.WriteError.NotDirectory

  data class NotFile(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.NotFile,
    IjentFileSystemApi.FileReaderError.NotFile,
    IjentFileSystemApi.FileWriterError.NotFile,
    IjentFileSystemApi.SameFileError.NotFile,
    IjentFileSystemApi.StatError.NotFile,
    IjentOpenedFile.Reader.ReadError.NotFile,
    IjentOpenedFile.SeekError.NotFile,
    IjentOpenedFile.Writer.WriteError.NotFile
}
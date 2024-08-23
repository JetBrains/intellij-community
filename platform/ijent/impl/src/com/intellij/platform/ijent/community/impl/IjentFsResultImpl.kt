// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.fs.*

@Suppress("unused") // Usages are to be implemented later.
object IjentFsResultImpl {
  data class Ok<T, E : IjentFsError>(override val value: T) : IjentFsResult.Ok<T, E>
  data class Error<T, E : IjentFsError>(override val error: E) : IjentFsResult.Error<T, E>

  data class BytesReadImpl(override val bytesRead: Int) : IjentOpenedFile.Reader.ReadResult.Bytes
  data object EOFImpl : IjentOpenedFile.Reader.ReadResult.EOF

  data class Other(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.FileReaderError.Other,
    IjentFileSystemApi.FileWriterError.Other,
    IjentFileSystemApi.ListDirectoryError.Other,
    IjentFileSystemApi.SameFileError.Other,
    IjentFileSystemApi.StatError.Other,
    IjentFileSystemApi.CanonicalizeError.Other,
    IjentOpenedFile.SeekError.Other,
    IjentOpenedFile.TellError.Other,
    IjentOpenedFile.Reader.ReadError.Other,
    IjentOpenedFile.Writer.WriteError.Other

  data class DoesNotExist(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.FileReaderError.DoesNotExist,
    IjentFileSystemApi.FileWriterError.DoesNotExist,
    IjentFileSystemApi.ListDirectoryError.DoesNotExist,
    IjentFileSystemApi.SameFileError.DoesNotExist,
    IjentFileSystemApi.StatError.DoesNotExist,
    IjentFileSystemApi.CanonicalizeError.DoesNotExist

  data class AlreadyExists(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.FileReaderError.AlreadyExists,
    IjentFileSystemApi.FileWriterError.AlreadyExists

  class PermissionDenied(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.PermissionDenied,
    IjentFileSystemApi.FileReaderError.PermissionDenied,
    IjentFileSystemApi.FileWriterError.PermissionDenied,
    IjentFileSystemApi.ListDirectoryError.PermissionDenied,
    IjentFileSystemApi.SameFileError.PermissionDenied,
    IjentFileSystemApi.StatError.PermissionDenied

  data class NotDirectory(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.NotDirectory,
    IjentFileSystemApi.FileReaderError.NotDirectory,
    IjentFileSystemApi.FileWriterError.NotDirectory,
    IjentFileSystemApi.ListDirectoryError.NotDirectory,
    IjentFileSystemApi.SameFileError.NotDirectory,
    IjentFileSystemApi.StatError.NotDirectory

  data class NotFile(override val where: IjentPath.Absolute, override val message: String) :
    IjentFileSystemApi.CanonicalizeError.NotFile,
    IjentFileSystemApi.FileReaderError.NotFile,
    IjentFileSystemApi.FileWriterError.NotFile,
    IjentFileSystemApi.SameFileError.NotFile,
    IjentFileSystemApi.StatError.NotFile

  data class InvalidValue(override val where: IjentPath.Absolute, override val message: String) :
    IjentOpenedFile.Reader.ReadError.InvalidValue,
    IjentOpenedFile.Writer.WriteError.InvalidValue,
    IjentOpenedFile.SeekError.InvalidValue

  data class UnknownFile(override val where: IjentPath.Absolute, override val message: String) :
    IjentOpenedFile.Reader.ReadError.UnknownFile,
    IjentOpenedFile.Writer.WriteError.UnknownFile,
    IjentOpenedFile.SeekError.UnknownFile
}
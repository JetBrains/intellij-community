// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.path.EelPath

@Suppress("unused") // Usages are to be implemented later.
object EelFsResultImpl {
  data class Ok<T>(override val value: T) : EelResult.Ok<T>
  data class Error<E : EelFsError>(override val error: E) : EelResult.Error<E>

  data class BytesReadImpl(override val bytesRead: Int) : EelOpenedFile.Reader.ReadResult.Bytes
  data object EOFImpl : EelOpenedFile.Reader.ReadResult.EOF

  data class DiskInfoImpl(override val totalSpace: ULong, override val availableSpace: ULong) : EelFileSystemApi.DiskInfo


  data class Other(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.FileReaderError.Other,
    EelFileSystemApi.FileWriterError.Other,
    EelFileSystemApi.ListDirectoryError.Other,
    EelFileSystemApi.SameFileError.Other,
    EelFileSystemApi.StatError.Other,
    EelFileSystemApi.CanonicalizeError.Other,
    EelFileSystemApi.CreateTemporaryDirectoryError.Other,
    EelOpenedFile.SeekError.Other,
    EelOpenedFile.TellError.Other,
    EelOpenedFile.Reader.ReadError.Other,
    EelOpenedFile.Writer.WriteError.Other,
    EelFileSystemApi.DiskInfoError.Other

  data class DoesNotExist(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.FileReaderError.DoesNotExist,
    EelFileSystemApi.FileWriterError.DoesNotExist,
    EelFileSystemApi.ListDirectoryError.DoesNotExist,
    EelFileSystemApi.SameFileError.DoesNotExist,
    EelFileSystemApi.StatError.DoesNotExist,
    EelFileSystemApi.CanonicalizeError.DoesNotExist,
    EelFileSystemApi.DiskInfoError.PathDoesNotExists

  data class AlreadyExists(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.FileReaderError.AlreadyExists,
    EelFileSystemApi.FileWriterError.AlreadyExists

  class PermissionDenied(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.CanonicalizeError.PermissionDenied,
    EelFileSystemApi.CreateTemporaryDirectoryError.PermissionDenied,
    EelFileSystemApi.FileReaderError.PermissionDenied,
    EelFileSystemApi.FileWriterError.PermissionDenied,
    EelFileSystemApi.ListDirectoryError.PermissionDenied,
    EelFileSystemApi.SameFileError.PermissionDenied,
    EelFileSystemApi.StatError.PermissionDenied,
    EelFileSystemApi.DiskInfoError.PermissionDenied

  data class NotDirectory(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.CanonicalizeError.NotDirectory,
    EelFileSystemApi.CreateTemporaryDirectoryError.NotDirectory,
    EelFileSystemApi.FileReaderError.NotDirectory,
    EelFileSystemApi.FileWriterError.NotDirectory,
    EelFileSystemApi.ListDirectoryError.NotDirectory,
    EelFileSystemApi.SameFileError.NotDirectory,
    EelFileSystemApi.StatError.NotDirectory

  data class NameTooLong(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.DiskInfoError.NameTooLong

  data class NotFile(override val where: EelPath.Absolute, override val message: String) :
    EelFileSystemApi.CanonicalizeError.NotFile,
    EelFileSystemApi.FileReaderError.NotFile,
    EelFileSystemApi.FileWriterError.NotFile,
    EelFileSystemApi.SameFileError.NotFile,
    EelFileSystemApi.StatError.NotFile

  data class InvalidValue(override val where: EelPath.Absolute, override val message: String) :
    EelOpenedFile.Reader.ReadError.InvalidValue,
    EelOpenedFile.Writer.WriteError.InvalidValue,
    EelOpenedFile.SeekError.InvalidValue

  data class UnknownFile(override val where: EelPath.Absolute, override val message: String) :
    EelOpenedFile.Reader.ReadError.UnknownFile,
    EelOpenedFile.Writer.WriteError.UnknownFile,
    EelOpenedFile.SeekError.UnknownFile
}
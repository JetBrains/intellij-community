// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.fs.IjentFileSystemApi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.io.IOException

/** Every API result must include all variants of this sealed interface. */
@ApiStatus.Experimental
sealed interface IjentFsResult {
  interface Ok<T> : IjentFsResult {
    val value: T
  }

  @NonExtendable
  interface ErrorBase {
    val where: IjentPath.Absolute
    val message: String
  }

  sealed interface Error : IjentFsResult, ErrorBase

  abstract class IjentFsIOException : IOException() {
    abstract val error: ErrorBase
    override val message: String get() = error.message
  }

  // TODO There should be a unit test that checks interfaces hierarchy.

  @NonExtendable
  interface DoesNotExist :
    Error,
    ListDirectory.DoesNotExist,
    ListDirectoryWithAttrs.DoesNotExist,
    Canonicalize.DoesNotExist,
    Stat.DoesNotExist,
    SameFile.DoesNotExist,
    FileReader.DoesNotExist,
    FileWriter.DoesNotExist,
    IjentOpenedFile.CloseException.CloseError.DoesNotExist,
    IjentOpenedFile.Seek.DoesNotExist,
    IjentOpenedFile.Reader.Read.DoesNotExist,
    IjentOpenedFile.Writer.Write.DoesNotExist,
    IjentOpenedFile.Writer.TruncateException.TruncateError.DoesNotExist

  @NonExtendable
  interface PermissionDenied :
    Error,
    ListDirectory.PermissionDenied,
    ListDirectoryWithAttrs.PermissionDenied,
    Canonicalize.PermissionDenied,
    Stat.PermissionDenied,
    SameFile.PermissionDenied,
    FileReader.PermissionDenied,
    FileWriter.PermissionDenied,
    IjentOpenedFile.CloseException.CloseError.PermissionDenied,
    IjentOpenedFile.Seek.PermissionDenied,
    IjentOpenedFile.Reader.Read.PermissionDenied,
    IjentOpenedFile.Writer.Write.PermissionDenied,
    IjentOpenedFile.Writer.TruncateException.TruncateError.PermissionDenied

  @NonExtendable
  interface NotDirectory :
    Error,
    ListDirectory.NotDirectory,
    ListDirectoryWithAttrs.NotDirectory,
    Canonicalize.NotDirectory,
    Stat.NotDirectory,
    SameFile.NotDirectory,
    FileReader.NotDirectory,
    FileWriter.NotDirectory,
    IjentOpenedFile.CloseException.CloseError.NotDirectory,
    IjentOpenedFile.Seek.NotDirectory,
    IjentOpenedFile.Reader.Read.NotDirectory,
    IjentOpenedFile.Writer.Write.NotDirectory,
    IjentOpenedFile.Writer.TruncateException.TruncateError.NotDirectory

  // TODO is this a really generic issue?
  @NonExtendable
  interface NotFile :
    Error,
    ListDirectory.NotFile,
    ListDirectoryWithAttrs.NotFile,
    Canonicalize.NotFile,
    Stat.NotFile,
    SameFile.NotFile,
    FileReader.NotFile,
    FileWriter.NotFile,
    IjentOpenedFile.CloseException.CloseError.NotFile,
    IjentOpenedFile.Seek.NotFile,
    IjentOpenedFile.Reader.Read.NotFile,
    IjentOpenedFile.Writer.Write.NotFile,
    IjentOpenedFile.Writer.TruncateException.TruncateError.NotFile
}
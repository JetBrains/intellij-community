// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFsResult
import com.intellij.platform.ijent.fs.IjentOpenedFile
import com.intellij.platform.ijent.fs.IjentPath
import org.jetbrains.annotations.ApiStatus.Internal

@Suppress("unused") // Usages are to be implemented later.
@Internal
object IjentFsResultImpl {
  data class DoesNotExist(override val where: IjentPath.Absolute, override val message: String) : IjentFsResult.DoesNotExist

  data class PermissionDenied(override val where: IjentPath.Absolute, override val message: String) : IjentFsResult.PermissionDenied

  data class NotDirectory(override val where: IjentPath.Absolute, override val message: String) : IjentFsResult.NotDirectory

  data class NotFile(override val where: IjentPath.Absolute, override val message: String) : IjentFsResult.NotFile

  object ListDirectory {
    data class Ok(override val value: Collection<String>) :
      IjentFileSystemApi.ListDirectory.Ok
  }

  object ListDirectoryWithAttrs {
    data class Ok(override val value: Collection<IjentFileSystemApi.FileInfo>) :
      IjentFileSystemApi.ListDirectoryWithAttrs.Ok
  }

 object SameFile {
   data class Ok(override val value: Boolean) :
     IjentFileSystemApi.SameFile.Ok
 }

  object FileReader {
    data class Ok(override val value: IjentOpenedFile.Reader) :
      IjentFileSystemApi.FileReader.Ok
  }

  object FileWriter {
    data class Ok(override val value: IjentOpenedFile.Writer) :
      IjentFileSystemApi.FileWriter.Ok
  }

  object Reader {
    object Read {
      data class Ok(override val value: Int) :
        IjentOpenedFile.Reader.Read.Ok
    }
  }

  object Writer {
    object Write {
      data class Ok(override val value: Int) :
        IjentOpenedFile.Writer.Write.Ok
    }
  }

  object Stat {
    data class Ok(override val value: IjentFileSystemApi.FileInfo) :
      IjentFileSystemApi.Stat.Ok
  }
}
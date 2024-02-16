// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

sealed interface IjentFileInfo {
  val path: IjentPath.Absolute
  val type: Type
  //val permissions: Permissions,  // TODO There are too many options for a good API. Let's add them as soon as they're needed.

  sealed interface Type {
    interface Directory : Type
    interface Regular : Type
    interface Other : Type
  }
}

interface IjentPosixFileInfo : IjentFileInfo {
  sealed interface Type : IjentFileInfo.Type {
    sealed interface Symlink : Type {
      interface Unresolved : Symlink
      interface Resolved : Symlink {
        val result: IjentPath.Absolute
      }
    }
  }
}

interface IjentWindowsFileInfo : IjentFileInfo
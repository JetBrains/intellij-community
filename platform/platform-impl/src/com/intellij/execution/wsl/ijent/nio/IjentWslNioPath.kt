// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import java.nio.file.Path

class IjentWslNioPath(private val fileSystem: IjentWslNioFileSystem,  val delegate: Path) : Path by delegate {
  override fun getFileSystem(): IjentWslNioFileSystem = fileSystem
}
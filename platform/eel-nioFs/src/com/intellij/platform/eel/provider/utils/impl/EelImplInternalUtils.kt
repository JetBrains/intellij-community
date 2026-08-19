// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils.impl

import com.intellij.platform.eel.channels.EelDelicateApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path


/**  [path] is `\\wsl.localhost\Ubuntu\mnt\c\Program Files`, then actual path is `C:\Program Files` */
@ApiStatus.Internal
@EelDelicateApi
fun getActualWslPath(path: Path): Path = path.run {
  if (
    isAbsolute &&
    nameCount >= 2 &&
    getName(0).toString() == "mnt" &&
    getName(1).toString().run { length == 1 && first().isLetter() }
  )
    asSequence()
      .drop(2)
      .map(Path::toString)
      .fold(fileSystem.getPath("${getName(1).toString().uppercase()}:\\"), Path::resolve)
  else
    this
}
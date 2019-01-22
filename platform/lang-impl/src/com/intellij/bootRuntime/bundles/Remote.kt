// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.bundles


import com.intellij.bootRuntime.command.*
import java.io.File


class Remote(remoteFileName: String) : Runtime(File(remoteFileName)) {

  override val fileName: String = remoteFileName

  override fun install() {
    Processor.process(
      Download(this),
      Extract(this),
      Copy(this),
      UpdatePath(this))
  }
}
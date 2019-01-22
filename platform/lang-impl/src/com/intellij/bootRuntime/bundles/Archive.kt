// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.bundles

import com.intellij.bootRuntime.command.Copy
import com.intellij.bootRuntime.command.Extract
import com.intellij.bootRuntime.command.Processor
import com.intellij.bootRuntime.command.UpdatePath
import java.io.File

class Archive(initialLocation: File) : Runtime(initialLocation) {

  override fun install() {
    Processor.process(
      Extract(this),
      Copy(this),
      UpdatePath(this))
  }
}
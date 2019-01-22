// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.bundles


import com.intellij.bootRuntime.command.Processor
import com.intellij.bootRuntime.command.UpdatePath
import java.io.File

class Local(location: File) : Runtime(location) {

  override val installationPath: File = location

  val version : String by lazy {
    // runs on first access of messageView
    fetchVersion()
  }

  override fun install() {
    Processor.process(UpdatePath(this))
  }

  override fun toString(): String {
    return "$version [Local]"
  }
}
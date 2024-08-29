/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key

class ProcessOutput(val text: String, kind: Key<*>) {
  private val kindValue: Int = when(kind) {
    ProcessOutputTypes.STDOUT -> 1
    ProcessOutputTypes.STDERR -> 2
    else -> 3
  }

  val kind: Key<*>
    get() = when (kindValue) {
      1 -> ProcessOutputType.STDOUT
      2 -> ProcessOutputType.STDERR
      else -> ProcessOutputType.SYSTEM
    }
}

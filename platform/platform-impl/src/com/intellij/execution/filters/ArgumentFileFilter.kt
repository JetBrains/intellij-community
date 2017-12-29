// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import java.io.File

/**
 * A console filter which looks for a given path in an output and creates a link for viewing a content of that file.
 */
class ArgumentFileFilter() : Filter {
  @Volatile private var filePath: String? = null
  @Volatile private var fileText: String? = null
  private var triggered = false

  constructor(filePath: String?, fileText: String?) : this() {
    this.filePath = filePath
    this.fileText = fileText
  }

  fun setPath(path: String) {
    filePath = path
    fileText = File(path).readText()
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    if (!triggered) {
      val path = this.filePath
      val text = this.fileText
      if (path == null || text == null) {
        triggered = true
      }
      else {
        val p = line.indexOf(path)
        if (p > 0) {
          triggered = true
          val offset = entireLength - line.length + p
          return Filter.Result(offset, offset + path.length, ShowTextPopupHyperlinkInfo(path, text))
        }
      }
    }

    return null
  }
}
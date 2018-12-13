// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import java.io.File
import java.nio.charset.Charset

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

  @JvmOverloads
  fun setPath(path: String, charset: Charset = Charsets.UTF_8) {
    filePath = path
    fileText = File(path).readText(charset)
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
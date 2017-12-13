/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.configurations

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient

import java.io.File
import java.nio.charset.Charset
import java.util.Collections
import java.util.regex.Pattern

/**
 * The information about a single log file displayed in the console when the configuration
 * is run.
 *
 * @since 5.1
 */
@Tag("log_file")
class LogFileOptions {
  @get:Attribute("alias")
  var name: String? = null
  @get:Attribute(value = "path", converter = PathConverter::class)
  var pathPattern: String? = null
  @get:Attribute("checked")
  var isEnabled = true
  @get:Attribute("skipped")
  var isSkipContent = true
  @get:Attribute("show_all")
  var isShowAll = false
  @get:Attribute(value = "charset", converter = CharsetConverter::class)
  var charset: Charset

  val paths: Set<String>
    @Transient
    get() {
      val logFile = File(pathPattern!!)
      if (logFile.exists()) {
        return setOf<String>(pathPattern)
      }

      val dirIndex = pathPattern!!.lastIndexOf(File.separator)
      if (dirIndex == -1) {
        return emptySet()
      }

      val files = SmartList<File>()
      collectMatchedFiles(File(pathPattern!!.substring(0, dirIndex)),
                          Pattern.compile(FileUtil.convertAntToRegexp(pathPattern!!.substring(dirIndex + File.separator.length))), files)
      if (files.isEmpty()) {
        return emptySet()
      }

      if (isShowAll) {
        val result = SmartHashSet<String>()
        result.ensureCapacity(files.size)
        for (file in files) {
          result.add(file.path)
        }
        return result
      }
      else {
        var lastFile: File? = null
        for (file in files) {
          if (lastFile != null) {
            if (file.lastModified() > lastFile.lastModified()) {
              lastFile = file
            }
          }
          else {
            lastFile = file
          }
        }
        assert(lastFile != null)
        return setOf(lastFile!!.path)
      }
    }

  private class PathConverter : Converter<String>() {
    override fun fromString(value: String): String? {
      return FileUtilRt.toSystemDependentName(value)
    }

    override fun toString(value: String): String {
      return FileUtilRt.toSystemIndependentName(value)
    }
  }

  private class CharsetConverter : Converter<Charset>() {
    override fun fromString(value: String): Charset? {
      try {
        return Charset.forName(value)
      }
      catch (ignored: Exception) {
        return Charset.defaultCharset()
      }

    }

    override fun toString(value: Charset): String {
      return value.name()
    }
  }

  //read external
  constructor() {
    charset = Charset.defaultCharset()
  }

  constructor(name: String, path: String, enabled: Boolean, skipContent: Boolean, showAll: Boolean) : this(name, path, null, enabled,
                                                                                                           skipContent, showAll) {
  }

  constructor(name: String, path: String, enabled: Boolean) : this(name, path, null, enabled, true, false) {}

  constructor(name: String, path: String, charset: Charset?, enabled: Boolean, skipContent: Boolean, showAll: Boolean) {
    this.name = name
    pathPattern = path
    isEnabled = enabled
    isSkipContent = skipContent
    isShowAll = showAll
    this.charset = charset ?: Charset.defaultCharset()
  }

  @Transient
  fun setLast(last: Boolean) {
    isShowAll = !last
  }

  companion object {

    fun collectMatchedFiles(root: File, pattern: Pattern, files: MutableList<File>) {
      val dirs = root.listFiles() ?: return
      for (dir in dirs) {
        if (pattern.matcher(dir.name).matches() && dir.isFile) {
          files.add(dir)
        }
      }
    }

    fun areEqual(options1: LogFileOptions?, options2: LogFileOptions?): Boolean {
      return if (options1 == null || options2 == null) {
        options1 === options2
      }
      else options1.name == options2.name &&
           options1.pathPattern == options2.pathPattern &&
           !options1.isShowAll == !options2.isShowAll &&
           options1.isEnabled == options2.isEnabled &&
           options1.isSkipContent == options2.isSkipContent

    }
  }
}

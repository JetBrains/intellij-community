/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * The information about a single log file displayed in the console when the configuration
 * is run.
 *
 * @since 5.1
 */
@Tag("log_file")
class LogFileOptions : BaseState {
  companion object {
    @JvmStatic
    fun collectMatchedFiles(root: File, pattern: Pattern, files: MutableList<File>) {
      val dirs = root.listFiles() ?: return
      dirs.filterTo(files) { pattern.matcher(it.name).matches() && it.isFile }
    }

    @JvmStatic
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

  @get:Attribute("alias")
  var name by string()

  @get:Attribute(value = "path", converter = PathConverter::class)
  var pathPattern by string()

  @get:Attribute("checked")
  var isEnabled by property(true)

  @get:Attribute("skipped")
  var isSkipContent by property(true)

  @get:Attribute("show_all")
  var isShowAll by property(false)

  @get:Attribute(value = "charset", converter = CharsetConverter::class)
  var charset: Charset by property(Charset.defaultCharset())

  fun getPaths(): Set<String> {
    val logFile = File(pathPattern!!)
    if (logFile.exists()) {
      return setOf(pathPattern!!)
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
      files.mapTo(result) { it.path }
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

  //read external
  constructor()

  @JvmOverloads
  constructor(name: String?, path: String?, enabled: Boolean = true, skipContent: Boolean = true, showAll: Boolean = false) : this(name, path, null, enabled, skipContent, showAll)

  @JvmOverloads
  constructor(name: String?, path: String?, charset: Charset?, enabled: Boolean = true, skipContent: Boolean = true, showAll: Boolean = false) {
    this.name = name
    pathPattern = path
    isEnabled = enabled
    isSkipContent = skipContent
    isShowAll = showAll
    this.charset = charset ?: Charset.defaultCharset()
  }

  fun setLast(last: Boolean) {
    isShowAll = !last
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
    return try {
      Charset.forName(value)
    }
    catch (ignored: Exception) {
      Charset.defaultCharset()
    }

  }

  override fun toString(value: Charset): String {
    return value.name()
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import java.nio.charset.Charset

/**
 * The information about a single log file displayed in the console when the configuration
 * is run.
 */
@Tag("log_file")
class LogFileOptions : BaseState {
  companion object {
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
  @get:NlsSafe
  var name: String? by string()

  @get:Attribute(value = "path", converter = PathConverter::class)
  @get:NlsSafe
  var pathPattern: String? by string()

  @get:Attribute("checked")
  var isEnabled: Boolean by property(true)

  @get:Attribute("skipped")
  var isSkipContent: Boolean by property(true)

  @get:Attribute("show_all")
  var isShowAll: Boolean by property(false)

  @get:Attribute(value = "charset", converter = CharsetConverter::class)
  var charset: Charset by property(Charset.defaultCharset())

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
  override fun fromString(value: String) = FileUtilRt.toSystemDependentName(value)

  override fun toString(value: String) = FileUtilRt.toSystemIndependentName(value)
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

  override fun toString(value: Charset) = value.name()
}
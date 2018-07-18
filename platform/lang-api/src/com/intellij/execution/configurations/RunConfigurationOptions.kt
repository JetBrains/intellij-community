/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.*

open class RunConfigurationOptions : BaseState() {
  @Tag("output_file")
  class OutputFileOptions : BaseState() {
    @get:Attribute("path")
    var fileOutputPath: String? by string()
    @get:Attribute("is_save")
    var isSaveOutput: Boolean by property(false)
  }

  // we use object instead of 2 fields because XML serializer cannot reuse tag for several fields
  @get:Property(surroundWithTag = false)
  var fileOutput: OutputFileOptions by property(OutputFileOptions())

  @get:Attribute("show_console_on_std_out")
  var isShowConsoleOnStdOut: Boolean by property(false)
  @get:Attribute("show_console_on_std_err")
  var isShowConsoleOnStdErr: Boolean by property(false)

  @get:Property(surroundWithTag = false)
  @get:XCollection
  var logFiles: MutableList<LogFileOptions> by list<LogFileOptions>()
}

open class LocatableRunConfigurationOptions : RunConfigurationOptions() {
  @get:Attribute("nameIsGenerated") var isNameGenerated: Boolean by property(false)
}

open class ModuleBasedConfigurationOptions : LocatableRunConfigurationOptions() {
  @get:OptionTag(tag = "module", valueAttribute = "name", nameAttribute = "")
  var module: String? by string()
}
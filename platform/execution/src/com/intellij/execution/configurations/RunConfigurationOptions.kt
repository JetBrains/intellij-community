// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.execution.ui.FragmentedSettings
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.*

@Tag("predefined_log_file")
class PredefinedLogFile() : BaseState() {
  constructor(id: String, isEnabled: Boolean) : this() {
    this.id = id
    this.isEnabled = isEnabled
  }

  @get:Attribute
  var id: String? by string()
  @get:Attribute("enabled")
  var isEnabled: Boolean by property(false)
}

open class RunConfigurationOptions : BaseState(), FragmentedSettings {
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

  @get:Property(surroundWithTag = false)
  @get:XCollection
  var predefinedLogFiles: MutableList<PredefinedLogFile> by list()

  @com.intellij.configurationStore.Property(description = "Show console when a message is printed to standard output stream")
  @get:Attribute("show_console_on_std_out")
  var isShowConsoleOnStdOut: Boolean by property(false)
  @com.intellij.configurationStore.Property(description = "Show console when a message is printed to standard error stream")
  @get:Attribute("show_console_on_std_err")
  var isShowConsoleOnStdErr: Boolean by property(false)

  @get:Property(surroundWithTag = false)
  @get:XCollection
  var logFiles: MutableList<LogFileOptions> by list()

  @com.intellij.configurationStore.Property(description = "Allow multiple instances")
  @get:Transient
  var isAllowRunningInParallel: Boolean by property(false)

  @get:OptionTag(tag = "target", valueAttribute = "name", nameAttribute = "")
  var remoteTarget: String? by string()

  @get:OptionTag(tag = "projectPathOnTarget")
  var projectPathOnTarget: String? by string()

  @get:XCollection(propertyElementName = "selectedOptions")
  override var selectedOptions: MutableList<FragmentedSettings.Option> by list()
}

open class LocatableRunConfigurationOptions : RunConfigurationOptions() {
  @com.intellij.configurationStore.Property(ignore = true)
  @get:Attribute("nameIsGenerated")
  var isNameGenerated: Boolean by property(false)
}

open class ModuleBasedConfigurationOptions : LocatableRunConfigurationOptions() {
  @get:OptionTag(tag = "module", valueAttribute = "name", nameAttribute = "")
  var module: String? by string()

  @get:XCollection(propertyElementName = "classpathModifications")
  var classpathModifications: MutableList<ClasspathModification> by list()

  @Tag("entry")
  class ClasspathModification() : BaseState() {
    constructor(path: String, exclude: Boolean): this() {
      this.path = path
      this.exclude = exclude
    }

    @get:Attribute("path")
    var path: String? by string()

    @get:Attribute("exclude")
    var exclude: Boolean by property(false)
  }
}
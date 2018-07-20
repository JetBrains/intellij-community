// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application

import com.intellij.execution.JvmConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XMap
import java.util.*

open class ApplicationConfigurationOptions : JvmConfigurationOptions() {
  @get:OptionTag("PROGRAM_PARAMETERS")
  open var programParameters: String? by string()

  @get:OptionTag("WORKING_DIRECTORY")
  open var workingDirectory: String? by string()

  @get:OptionTag("INCLUDE_PROVIDED_SCOPE")
  var includeProvidedScope: Boolean by property(false)

  @get:OptionTag("ENABLE_SWING_INSPECTOR")
  var isSwingInspectorEnabled: Boolean by property(false)

  @get:OptionTag("PASS_PARENT_ENVS")
  var isPassParentEnv: Boolean by property(true)

  @get:XMap(propertyElementName = "envs", entryTagName = "env", keyAttributeName = "name")
  var env: MutableMap<String, String> by property(LinkedHashMap<String, String>())
}
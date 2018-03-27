/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application

import com.intellij.execution.JvmConfigurationOptions
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.*

open class ApplicationConfigurationOptions : JvmConfigurationOptions() {
  @get:OptionTag("PROGRAM_PARAMETERS")
  open var programParameters by string()

  @get:OptionTag("WORKING_DIRECTORY")
  open var workingDirectory by string()

  @get:OptionTag("INCLUDE_PROVIDED_SCOPE")
  var includeProvidedScope by property(false)

  @get:OptionTag("ENABLE_SWING_INSPECTOR")
  var isSwingInspectorEnabled by property(false)

  @get:OptionTag("PASS_PARENT_ENVS")
  var isPassParentEnv by property(true)

  @get:MapAnnotation(propertyElementName = "envs", entryTagName = "env", keyAttributeName = "name", sortBeforeSave = false)
  var env by property(LinkedHashMap<String, String>())
}
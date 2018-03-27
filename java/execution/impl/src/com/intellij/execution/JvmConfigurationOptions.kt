/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution

import com.intellij.execution.configurations.ModuleBasedConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag

abstract class JvmConfigurationOptions : ModuleBasedConfigurationOptions() {
  @get:OptionTag("MAIN_CLASS_NAME")
  var mainClassName by string()

  @get:OptionTag("VM_PARAMETERS")
  var vmParameters by string()

  @get:OptionTag("ALTERNATIVE_JRE_PATH")
  var alternativeJrePath by string()

  @get:OptionTag("ALTERNATIVE_JRE_PATH_ENABLED")
  var isAlternativeJrePathEnabled by property(false)
}
/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.applet

import com.intellij.execution.ExternalizablePath
import com.intellij.execution.JvmConfigurationOptions
import com.intellij.openapi.application.PathManager
import com.intellij.util.xmlb.annotations.*

class AppletConfigurationOptions : JvmConfigurationOptions() {
  @get:OptionTag("HTML_FILE_NAME")
  var htmlFileName: String? by string()

  @get:OptionTag("HTML_USED")
  var htmlUsed: Boolean by property(false)

  @get:OptionTag("WIDTH")
  var width: Int by property(400)

  @get:OptionTag("HEIGHT")
  var height: Int by property(300)

  @get:OptionTag("POLICY_FILE")
  var policyFile: String? by string(ExternalizablePath.urlValue("${PathManager.getHomePath()}/bin/appletviewer.policy"))

  @get:Property(surroundWithTag = false)
  @get:XCollection()
  var appletParameters: MutableList<AppletParameter> by list()
}

@Tag("parameter")
data class AppletParameter(@get:Attribute("name") var name: String? = null, @get:Attribute("value") var value: String? = null)
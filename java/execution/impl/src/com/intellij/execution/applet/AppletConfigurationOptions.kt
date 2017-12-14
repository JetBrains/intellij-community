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
  var htmlFileName by string()

  @get:OptionTag("HTML_USED")
  var htmlUsed by property(false)

  @get:OptionTag("WIDTH")
  var width by property(400)

  @get:OptionTag("HEIGHT")
  var height by property(300)

  @get:OptionTag("POLICY_FILE")
  var policyFile by string(ExternalizablePath.urlValue("${PathManager.getHomePath()}/bin/appletviewer.policy"))

  @get:Property(surroundWithTag = false)
  @get:XCollection()
  var appletParameters by list<AppletParameter>()
}

@Tag("parameter")
data class AppletParameter(@get:Attribute("name") var name: String? = null, @get:Attribute("value") var value: String? = null)
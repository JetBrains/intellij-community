// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application

import com.intellij.configurationStore.Property
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.JvmConfigurationOptions
import com.intellij.execution.ShortenCommandLine
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XMap

open class JvmMainMethodRunConfigurationOptions : JvmConfigurationOptions() {
  @get:OptionTag("PROGRAM_PARAMETERS")
  open var programParameters by string()

  @get:OptionTag("WORKING_DIRECTORY")
  open var workingDirectory by string()

  @get:OptionTag("INCLUDE_PROVIDED_SCOPE")
  open var isIncludeProvidedScope by property(false)

  @get:OptionTag("PASS_PARENT_ENVS")
  var isPassParentEnv by property(true)

  @Property(description = "Environment variables")
  @get:XMap(propertyElementName = "envs", entryTagName = "env", keyAttributeName = "name")
  var env by linkedMap<String, String>()

  // see ConfigurationWithCommandLineShortener - "null if option was not selected explicitly, legacy user-local options to be used"
  // so, we cannot use NONE as default value
  @get:OptionTag(nameAttribute = "", valueAttribute = "name")
  var shortenClasspath by enum<ShortenCommandLine>()

  @get:OptionTag(InputRedirectAware.InputRedirectOptionsImpl.REDIRECT_INPUT)
  var isRedirectInput by property(false)
  @get:OptionTag(InputRedirectAware.InputRedirectOptionsImpl.INPUT_FILE)
  var redirectInputPath by string()

  @Transient
  val redirectOptions = object : InputRedirectAware.InputRedirectOptions {
    override fun isRedirectInput() = this@JvmMainMethodRunConfigurationOptions.isRedirectInput

    override fun setRedirectInput(value: Boolean) {
      this@JvmMainMethodRunConfigurationOptions.isRedirectInput = value
    }

    override fun getRedirectInputPath() = this@JvmMainMethodRunConfigurationOptions.redirectInputPath

    override fun setRedirectInputPath(value: String?) {
      this@JvmMainMethodRunConfigurationOptions.redirectInputPath = value
    }
  }
}
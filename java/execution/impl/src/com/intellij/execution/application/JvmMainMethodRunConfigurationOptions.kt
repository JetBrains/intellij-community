// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application

import com.intellij.configurationStore.Property
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.JvmConfigurationOptions
import com.intellij.execution.ShortenCommandLine
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap

open class JvmMainMethodRunConfigurationOptions : JvmConfigurationOptions() {
  @get:OptionTag("PROGRAM_PARAMETERS")
  open var programParameters: String? by string()

  @get:OptionTag("WORKING_DIRECTORY")
  open var workingDirectory: String? by string()

  @get:OptionTag("INCLUDE_PROVIDED_SCOPE")
  open var isIncludeProvidedScope: Boolean by property(false)

  @get:OptionTag("UNNAMED_CLASS_CONFIGURATION")
  open var isImplicitClassConfiguration: Boolean by property(false)

  @get:OptionTag("PASS_PARENT_ENVS")
  var isPassParentEnv: Boolean by property(true)

  @Property(description = "Environment variables")
  @get:XMap(propertyElementName = "envs", entryTagName = "env", keyAttributeName = "name")
  var env: MutableMap<String, String> by linkedMap()

  @get:XCollection
  var envFilePaths: MutableList<String> by list()

  // see ConfigurationWithCommandLineShortener - "null if option was not selected explicitly, legacy user-local options to be used"
  // so, we cannot use NONE as the default value
  @get:OptionTag(nameAttribute = "", valueAttribute = "name")
  var shortenClasspath: ShortenCommandLine? by enum<ShortenCommandLine>()

  @get:OptionTag(InputRedirectAware.InputRedirectOptionsImpl.REDIRECT_INPUT)
  var isRedirectInput: Boolean by property(false)

  @get:OptionTag(InputRedirectAware.InputRedirectOptionsImpl.INPUT_FILE)
  var redirectInputPath: String? by string()

  @Transient
  val redirectOptions: InputRedirectAware.InputRedirectOptions = object : InputRedirectAware.InputRedirectOptions {
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

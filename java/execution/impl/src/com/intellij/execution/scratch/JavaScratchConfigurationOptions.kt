// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.execution.application.ApplicationConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag

open class JavaScratchConfigurationOptions: ApplicationConfigurationOptions() {
  @get:OptionTag("SCRATCH_FILE_URL")
  open var scratchFileUrl: String? by string()
}
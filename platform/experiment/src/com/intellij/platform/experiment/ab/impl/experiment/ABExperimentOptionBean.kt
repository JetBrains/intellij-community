// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute

class ABExperimentOptionBean : BaseKeyedLazyInstance<ABExperimentOption>() {
  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementationClass: String? = null

  override fun getImplementationClassName(): String? {
    return implementationClass
  }
}
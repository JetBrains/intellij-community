// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class MLCompletionCorrectnessSupporterEP : CustomLoadingExtensionPointBean<MLCompletionCorrectnessSupporter>,
                                           KeyedLazyInstance<MLCompletionCorrectnessSupporter> {
  @RequiredElement
  @Attribute("language")
  var language: String? = null

  @RequiredElement
  @Attribute("implementationClass")
  var implementationClass: String? = null

  @Suppress("unused")
  constructor() : super()

  @Suppress("unused")
  @TestOnly
  constructor(
    language: String?,
    implementationClass: String?,
    instance: MLCompletionCorrectnessSupporter,
  ) : super(instance) {
    this.language = language
    this.implementationClass = implementationClass
  }

  override fun getImplementationClassName(): String? = this.implementationClass
  override fun getKey(): String = language!!

  companion object {
    val EP_NAME: ExtensionPointName<MLCompletionCorrectnessSupporterEP> = ExtensionPointName.create(
      "com.intellij.mlCompletionCorrectnessSupporter")
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.serialization.PropertyMapping

class JavaManifestData {

  val manifestAttributes: Map<String, String>

  @PropertyMapping("manifestAttributes")
  constructor(manifestAttributes: Map<String, String>) {
    this.manifestAttributes = manifestAttributes
  }

  companion object {
    @JvmField
    val KEY = Key.create(JavaManifestData::class.java, ProjectKeys.TASK.processingWeight + 1)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.serialization.PropertyMapping

public class JavaManifestData {

  public val manifestAttributes: Map<String, String>

  @PropertyMapping("manifestAttributes")
  public constructor(manifestAttributes: Map<String, String>) {
    this.manifestAttributes = manifestAttributes
  }

  public companion object {
    @JvmField
    public val KEY: Key<JavaManifestData> = Key.create(JavaManifestData::class.java, ProjectKeys.TASK.processingWeight + 1)
  }
}
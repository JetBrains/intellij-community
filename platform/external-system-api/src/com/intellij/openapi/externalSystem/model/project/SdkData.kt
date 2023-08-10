// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.serialization.PropertyMapping

sealed class SdkData(var sdkName: String?) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SdkData) return false

    if (sdkName != other.sdkName) return false

    return true
  }

  override fun hashCode(): Int {
    return sdkName.hashCode()
  }
}

class ProjectSdkData
@PropertyMapping("sdkName")
constructor(
  sdkName: String?
) : SdkData(sdkName) {
  companion object {
    @JvmField
    val KEY: Key<ProjectSdkData> = Key.create(ProjectSdkData::class.java, ProjectKeys.PROJECT.processingWeight + 1)
  }
}

class ModuleSdkData
@PropertyMapping("sdkName")
constructor(
  sdkName: String?
) : SdkData(sdkName) {
  companion object {
    @JvmField
    val KEY: Key<ModuleSdkData> = Key.create(ModuleSdkData::class.java, ProjectSdkData.KEY.processingWeight + 1)
  }
}
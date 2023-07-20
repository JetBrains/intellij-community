// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.pom.java.LanguageLevel
import com.intellij.serialization.PropertyMapping

class JavaModuleData
@PropertyMapping("owner", "languageLevel", "targetBytecodeVersion")
constructor(
  owner: ProjectSystemId,
  var languageLevel: LanguageLevel?,
  var targetBytecodeVersion: String?
) : AbstractExternalEntityData(owner) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is JavaModuleData) return false
    if (!super.equals(other)) return false

    if (languageLevel != other.languageLevel) return false
    if (targetBytecodeVersion != other.targetBytecodeVersion) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + languageLevel.hashCode()
    result = 31 * result + targetBytecodeVersion.hashCode()
    return result
  }

  override fun toString(): String = "java module"

  companion object {
    @JvmField
    val KEY: Key<JavaModuleData> = Key.create(JavaModuleData::class.java, JavaProjectData.KEY.processingWeight + 1)
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.util.PathUtil
import org.jetbrains.annotations.Nls

data class ExternalSystemProjectId(
  val systemId: ProjectSystemId,
  val externalProjectPath: String
) {

  private val systemName: @Nls String = systemId.readableName

  val projectName: @Nls String = PathUtil.getFileName(externalProjectPath)

  @Deprecated("Use ExternalSystemProjectId#toString instead", replaceWith = ReplaceWith("this.toString()"))
  val debugName: String = "$systemName ($projectName)"

  override fun toString(): String {
    return "$systemName ($projectName)"
  }
}

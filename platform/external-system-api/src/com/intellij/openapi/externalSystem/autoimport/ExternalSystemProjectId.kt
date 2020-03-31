// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class ExternalSystemProjectId(val systemId: ProjectSystemId, val externalProjectPath: String) {
  val readableName = "${systemId.readableName} (${PathUtil.getFileName(externalProjectPath)})"
}

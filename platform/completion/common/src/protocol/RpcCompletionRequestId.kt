// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.platform.project.ProjectId
import fleet.util.UID
import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionRequestId(
  val id: UID = UID.random(),
  val projectId: ProjectId,
) {
  override fun toString(): String = buildToString("RpcCompletionRequestId") {
    field("id", id)
    field("projectId", projectId)
  }
}

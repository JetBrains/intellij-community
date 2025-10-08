// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ExternalProjectEntityId(
  private val externalProjectPath: String,
) : SymbolicEntityId<ExternalProjectEntity> {
  override val presentableName: @NlsSafe String
    get() = externalProjectPath

  @Transient
  private var codeCache: Int = 0


  override fun toString(): String {
    return "ExternalProjectEntityId(externalProjectPath='$externalProjectPath')"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExternalProjectEntityId

    if (codeCache != other.codeCache) return false
    if (externalProjectPath != other.externalProjectPath) return false

    return true
  }

  override fun hashCode(): Int {
    var result = codeCache
    result = 31 * result + externalProjectPath.hashCode()
    return result
  }
}
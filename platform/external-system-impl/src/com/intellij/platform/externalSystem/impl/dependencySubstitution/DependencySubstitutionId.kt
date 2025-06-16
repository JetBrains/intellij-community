// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.SymbolicEntityId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DependencySubstitutionId(
  val owner: ModuleId,
  val module: ModuleId,
  val scope: DependencyScope,
) : SymbolicEntityId<DependencySubstitutionEntity> {

  override val presentableName: String
    get() = module.name

  @Transient
  private var codeCache: Int = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DependencySubstitutionId) return false

    if (codeCache != other.codeCache) return false
    if (owner != other.owner) return false
    if (module != other.module) return false
    if (scope != other.scope) return false

    return true
  }

  override fun hashCode(): Int {
    var result = codeCache
    result = 31 * result + owner.hashCode()
    result = 31 * result + module.hashCode()
    result = 31 * result + scope.hashCode()
    return result
  }

  override fun toString(): String {
    return "DependencySubstitutionId(owner=$owner, substitution=$module, scope=$scope)"
  }
}

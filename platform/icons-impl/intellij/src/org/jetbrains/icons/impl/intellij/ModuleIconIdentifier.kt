// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import kotlinx.serialization.Serializable
import org.jetbrains.icons.IconIdentifier

@Serializable
class ModuleIconIdentifier(
  val pluginId: String,
  val moduleId: String?,
  val uniqueId: IconIdentifier
): IconIdentifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModuleIconIdentifier

    if (pluginId != other.pluginId) return false
    if (moduleId != other.moduleId) return false
    if (uniqueId != other.uniqueId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = pluginId.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + uniqueId.hashCode()
    return result
  }

  override fun toString(): String {
    return "ModuleIconIdentifier(pluginId='$pluginId', moduleId='$moduleId', uniqueId='$uniqueId')"
  }
}

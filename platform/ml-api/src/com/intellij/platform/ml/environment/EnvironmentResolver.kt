// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.environment

import com.intellij.platform.ml.Tier
import org.jetbrains.annotations.ApiStatus

/**
 * There was circle within available extenders' set's requirements.
 * Meaning that to create some tier, we must eventually run the extender itself.
 */
@ApiStatus.Internal
class CircularRequirementException(private val extensionPath: List<EnvironmentExtender<*>>) : IllegalArgumentException() {
  override val message: String = "A circular resolve path found among EnvironmentExtenders: ${serializePath()}"

  private fun serializePath(): String {
    val extensions = extensionPath.map { extender -> "[$extender] -> ${extender.extendingTier.name}" }
    return extensions.joinToString(" - ")
  }
}

/**
 * An algorithm for resolving order of [EnvironmentExtender]s' execution.
 */
internal interface EnvironmentResolver {
  /**
   * @return The order, which guarantees that for each extender, all the requirements will be fulfilled by the previously runned
   * extenders.
   * But it still could happen that an [EnvironmentExtender] will not return the tier it extends, then some subsequent
   * extenders' requirements could not be satisfied.
   * @throws CircularRequirementException
   */
  fun resolve(extenderPerTier: Map<Tier<*>, EnvironmentExtender<*>>): List<EnvironmentExtender<*>>
}

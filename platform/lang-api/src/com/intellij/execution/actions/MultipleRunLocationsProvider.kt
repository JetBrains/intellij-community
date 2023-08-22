// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions

import com.intellij.execution.Location
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls

/**
 * Allows to provide provide alternative locations that context run configurations will be created from.
 */
abstract class MultipleRunLocationsProvider {
  /**
   * @return A list of locations that should be used instead of [originalLocation] to create context run configurations.
   */
  abstract fun getAlternativeLocations(originalLocation: Location<*>): List<Location<*>>

  /**
   * @param locationCreatedFrom - location to get name for.
   * @param originalLocation - original location that [locationCreatedFrom] was derived from.
   *
   * @return Display name for [locationCreatedFrom] that will be presented to the user
   * so they can distinguish between run configurations created from a single place in code.
   */
  @Nls
  abstract fun getLocationDisplayName(
    locationCreatedFrom: Location<*>,
    originalLocation: Location<*>
  ): String?

  data class AlternativeLocationsInfo(
    val provider: MultipleRunLocationsProvider,
    val alternativeLocations: List<Location<*>>
  )

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MultipleRunLocationsProvider> = ExtensionPointName.create("com.intellij.multipleRunLocationsProvider")

    /**
     * @param originalLocation - location to calculate alternative location for.
     *
     * @return AlternativeLocationsInfo object containing alternative locations for [originalLocation] and
     * MultipleRunLocationsProvider instance that provided them.
     * Returns null if no MultipleRunLocationsProvider provided alternative locations for [originalLocation].
     */
    @JvmStatic
    fun findAlternativeLocations(originalLocation: Location<*>): AlternativeLocationsInfo? {
      for (extension in EP_NAME.extensionList) {
        val alternativeLocations = extension.getAlternativeLocations(originalLocation)
        if (alternativeLocations.isNotEmpty()) return AlternativeLocationsInfo(extension, alternativeLocations)
      }
      return null
    }
  }
}
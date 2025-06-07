// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable


/**
 * Marker interface to identify essential contributors for the Search Everywhere feature.
 *
 * This is an internal API used within the IntelliJ platform to mark certain contributors as essential.
 * It helps in determining which contributors should be prioritized.
 */
@IntellijInternalApi
@ApiStatus.Internal
interface SearchEverywhereEssentialContributorMarker {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereEssentialContributorMarker> = ExtensionPointName.create("com.intellij.searchEverywhereEssentialContributorsMarker")

    /**
     * Returns the instance of [SearchEverywhereEssentialContributorMarker] if available, null otherwise
     */
    @JvmStatic
    @Nullable
    fun getInstanceOrNull(): SearchEverywhereEssentialContributorMarker? {
      val extensions = EP_NAME.extensionList
      if (extensions.size > 1) {
        thisLogger().warn("Multiple implementations of ${SearchEverywhereEssentialContributorMarker::class.java.name}. Using the first.")
      }

      return extensions.firstOrNull { it.isAvailable() }
    }
  }

  /**
   * Returns true if the marker is available for use, false otherwise.
   */
  fun isAvailable(): Boolean

  /**
   * Returns true if the contributor is believed to be essential, false if not.
   * If the marker cannot compute the essentialness, null will be returned.
   */
  fun isContributorEssential(contributor: SearchEverywhereContributor<*>): Boolean?
}

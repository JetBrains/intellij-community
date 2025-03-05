package com.intellij.microservices

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface MicroservicesFeaturesAvailabilityProvider {
  /**
   * Decides whether Search Everywhere should have a separate tab for URL search in this project
   */
  @RequiresEdt
  fun searchEverywhereTabAvailableFor(project: Project): Boolean
}

private val EP_NAME: ExtensionPointName<MicroservicesFeaturesAvailabilityProvider> = ExtensionPointName.create(
  "com.intellij.microservices.featuresAvailabilityProvider"
)

@ApiStatus.Internal
fun isSearchEverywhereAvailableExplicitly(project: Project): Boolean {
  return EP_NAME.extensionList.any { it.searchEverywhereTabAvailableFor(project) }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkFixAction
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.util.Consumer
import org.jetbrains.annotations.Contract

/**
 * Use this service to resolve an SDK request to a given component allowing
 * the platform to guess or automatically suggest a possible option (or wait
 * for an option to be completed).
 *
 * The lookup process can take some time to resolve. The code can be executed
 * from any thread. There is no guaranty callbacks happen from EDT thread too.
 */
interface SdkLookup {
  fun createBuilder(): SdkLookupBuilder

  fun lookup(lookup: SdkLookupParameters)
  fun lookupBlocking(lookup: SdkLookupParameters)

  companion object {
    @JvmStatic
    fun newLookupBuilder(): SdkLookupBuilder = getInstance().createBuilder()

    @JvmStatic
    fun getInstance() = service<SdkLookup>()
  }
}

enum class SdkLookupDownloadDecision {
  /** ignore this SDK and continue lookup, it may end with the same SDK Download suggestiom **/
  SKIP,

  /** stop lookup on this callback, it will notify other callbacks with null **/
  STOP,

  /** wait for the SDK to complete download, it may deadlock if executed from a modal dialog/progress **/
  WAIT
}

enum class SdkLookupDecision {
  STOP,
  CONTINUE
}

interface SdkLookupBuilder {
  /**
   * Terminal operator of the builder to execute the search
   */
  fun executeLookup()

  @Contract(pure = true)
  fun withProgressIndicator(indicator: ProgressIndicator): SdkLookupBuilder

  @Contract(pure = true)
  fun withProject(project: Project?): SdkLookupBuilder

  @Contract(pure = true)
  fun withProgressMessageTitle(@ProgressTitle message: String): SdkLookupBuilder

  /**
   * Use these SDKs to test first, the [withSdkName] option has a higher priority
   **/
  @Contract(pure = true)
  fun testSuggestedSdksFirst(sdks: Sequence<Sdk?>): SdkLookupBuilder

  @Contract(pure = true)
  fun testSuggestedSdkFirst(sdk: () -> Sdk?): SdkLookupBuilder

  @Contract(pure = true)
  fun withSdkName(name: String): SdkLookupBuilder

  @Contract(pure = true)
  fun withSdkType(sdkType: SdkType): SdkLookupBuilder

  @Contract(pure = true)
  fun withVersionFilter(filter: (String) -> Boolean): SdkLookupBuilder

  @Contract(pure = true)
  fun withSdkHomeFilter(filter: (String) -> Boolean): SdkLookupBuilder

  /**
   * A notification that is invoked at the moment when a downloading SDK
   * is detected. It is a good chance the SDK matches the requested parameters
   */
  @Contract(pure = true)
  fun onDownloadingSdkDetected(handler : (Sdk) -> SdkLookupDownloadDecision) : SdkLookupBuilder

  /**
   * A notification that is invoked at the moment where we failed to find
   * a suitable SDK from a given name, project. At that moment we star
   * looking for a possible SDK suggestions. Return `false` from the
   * callback to stop the search.
   */
  @Contract(pure = true)
  fun onBeforeSdkSuggestionStarted(handler: () -> SdkLookupDecision): SdkLookupBuilder

  /**
   * Called on each detected SDK local candidate, that matches filters
   */
  @Contract(pure = true)
  fun onLocalSdkSuggested(handler: (UnknownSdkLocalSdkFix) -> SdkLookupDecision): SdkLookupBuilder

  /**
   * Called on each detected SDK download candidate, that matches filters
   */
  @Contract(pure = true)
  fun onDownloadableSdkSuggested(handler: (UnknownSdkDownloadableSdkFix) -> SdkLookupDecision): SdkLookupBuilder

  /**
   * And SDK resolution and delivery process can take time,
   * this callback is executed once we know for sure the exact name
   * and version of an SDK, but it's still on the way
   *
   * The callback is executed with `null` if the SDK search
   * failed, cancelled or returned no elements
   *
   * It is guaranteed that this method is executed before [onSdkResolved] callback
   *
   * [withSdkHomeFilter] is not tested for this call!
   */
  @Contract(pure = true)
  fun onSdkNameResolved(handler: (Sdk?) -> Unit) : SdkLookupBuilder

  /**
   * The [Sdk.getSdkType] may not match the proposed sdk type [withSdkType] if the
   * same named SDK already exists. It is up to this code client
   * to resolve that situation.
   *
   * The callback is executed with `null` if the SDK search
   * failed, cancelled or returned no elements.
   *
   * It is a possible case that due to the [withSdkHomeFilter] this callback
   * may be notified with `null`, but the [onSdkNameResolved] was executed with
   * a not null.
   */
  @Contract(pure = true)
  fun onSdkResolved(handler: (Sdk?) -> Unit): SdkLookupBuilder

  /**
   * This callback is executed before the resolved Sdk fix is found
   */
  @Contract(pure = true)
  fun onSdkFixResolved(handler: (UnknownSdkFixAction) -> SdkLookupDecision) : SdkLookupBuilder
}

interface SdkLookupParameters {
  val project: Project?

  val progressMessageTitle: String?
  val progressIndicator: ProgressIndicator?

  val sdkName: String?

  val sdkType: SdkType?

  val onDownloadingSdkDetected : (Sdk) -> SdkLookupDownloadDecision
  val onBeforeSdkSuggestionStarted: () -> SdkLookupDecision
  val onLocalSdkSuggested: (UnknownSdkLocalSdkFix) -> SdkLookupDecision
  val onDownloadableSdkSuggested: (UnknownSdkDownloadableSdkFix) -> SdkLookupDecision
  val onSdkFixResolved : (UnknownSdkFixAction) -> SdkLookupDecision

  val sdkHomeFilter: ((String) -> Boolean)?
  val versionFilter: ((String) -> Boolean)?

  val testSdkSequence: Sequence<Sdk?>

  val onSdkNameResolved: (Sdk?) -> Unit
  val onSdkResolved: (Sdk?) -> Unit
}

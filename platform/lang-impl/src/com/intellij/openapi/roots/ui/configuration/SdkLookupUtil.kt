// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("SdkLookupUtil")
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * Finds sdk at everywhere with parameters that defined by [configure]
 *
 * Note: this function blocks the current thread until the sdk is resolved
 *
 * Note: SdkLookupBuilder is immutable
 */
fun lookupSdkBlocking(configure: (SdkLookupBuilder) -> SdkLookupBuilder): Sdk? {
  val provider = SdkLookupProviderImpl()
  var builder = provider.newLookupBuilder()
  builder = configure(builder)
  builder.executeLookup()
  provider.waitForLookup()
  return provider.getSdk()
}

/**
 * Finds sdk at everywhere with parameters with defined parameters.
 *
 * Note: this function blocks the current thread until the sdk is resolved
 */
fun lookupAndSetupSdkBlocking(project: Project, indicator: ProgressIndicator, sdkType: SdkType, applySdk: Consumer<Sdk>) {
  val sdk = lookupSdkBlocking {
    it.withProgressIndicator(indicator)
      .withSdkType(sdkType)
      .withVersionFilter { true }
      .withProject(project)
      .onDownloadableSdkSuggested { SdkLookupDecision.STOP }
  }

  try {
    if (sdk != null) {
      WriteAction.runAndWait(
        ThrowableRunnable { applySdk.accept(sdk) }
      )
    }
  }
  catch (t: Throwable) {
    Logger.getInstance("sdk.lookup").warn("Couldn't lookup for a SDK", t)
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use lookupSdkBlocking instead", ReplaceWith("lookupSdkBlocking(configure)"))
fun lookupSdk(configure: (SdkLookupBuilder) -> SdkLookupBuilder): Sdk? {
  return lookupSdkBlocking(configure)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use lookupAndSetupSdk instead", ReplaceWith("lookupAndSetupSdkBlocking(project, indicator, sdkType, applySdk)"))
fun findAndSetupSdk(project: Project, indicator: ProgressIndicator, sdkType: SdkType, applySdk: (Sdk) -> Unit) {
  lookupAndSetupSdkBlocking(project, indicator, sdkType, applySdk)
}
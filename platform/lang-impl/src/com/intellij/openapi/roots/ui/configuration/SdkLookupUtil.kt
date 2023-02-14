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
import java.util.concurrent.CompletableFuture

/**
 * Finds sdk at everywhere with parameters that defined by [configure]
 *
 * Note: this function block current thread until sdk is resolved
 */
fun lookupSdk(configure: (SdkLookupBuilder) -> SdkLookupBuilder): Sdk? {
  val provider = SdkLookupProviderImpl()
  var builder = provider.newLookupBuilder()
  builder = configure(builder)
  builder.executeLookup()
  return provider.blockingGetSdk()
}

fun findAndSetupSdk(project: Project, indicator: ProgressIndicator, sdkType: SdkType, applySdk: (Sdk) -> Unit) {
  val future = CompletableFuture<Sdk>()
  SdkLookup.newLookupBuilder()
    .withProgressIndicator(indicator)
    .withSdkType(sdkType)
    .withVersionFilter { true }
    .withProject(project)
    .onDownloadableSdkSuggested { SdkLookupDecision.STOP }
    .onSdkResolved {
      future.complete(it)
    }
    .executeLookup()

  try {
    val sdk = future.get()
    if (sdk != null) {
      WriteAction.runAndWait(
        ThrowableRunnable { applySdk.invoke(sdk) }
      )
    }
  }
  catch (t: Throwable) {
    Logger.getInstance("sdk.lookup").warn("Couldn't lookup for a SDK", t)
  }
}
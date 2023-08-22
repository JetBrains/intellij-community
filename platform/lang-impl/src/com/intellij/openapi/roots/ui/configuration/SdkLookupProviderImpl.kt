// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class SdkLookupProviderImpl : SdkLookupProvider {

  @Volatile
  private var context: SdkLookupContext? = null

  override val progressIndicator: ProgressIndicator?
    get() = context?.progressIndicator

  override fun newLookupBuilder(): SdkLookupBuilder {
    return CommonSdkLookupBuilder(lookup = ::lookup)
  }

  @TestOnly
  fun getSdkPromiseForTests(): AsyncPromise<Sdk?>? = context?.getSdkPromiseForTests()

  override fun getSdkInfo(): SdkInfo {
    return context?.getSdkInfo() ?: SdkInfo.Undefined
  }

  override fun getSdk(): Sdk? {
    return context?.getSdk()
  }

  override fun blockingGetSdk(): Sdk? {
    return context?.blockingGetSdk()
  }

  private fun lookup(builder: CommonSdkLookupBuilder) {
    val progressIndicator = builder.progressIndicator ?: ProgressIndicatorBase()
    val context = SdkLookupContext(progressIndicator)
    this.context = context

    val parameters = builder
      .copy(
        progressIndicator = context.progressIndicator,
        //listeners are merged, so copy is the way to avoid chaining the listeners
        onSdkNameResolved = { sdk ->
          context.setSdkInfo(sdk)
          builder.onSdkNameResolved(sdk)
        },
        onSdkResolved = { sdk ->
          context.setSdk(sdk)
          builder.onSdkResolved(sdk)
        }
      )
    service<SdkLookup>().lookup(parameters)
  }

  private class SdkLookupContext(val progressIndicator: ProgressIndicator) {
    private val sdk = AsyncPromise<Sdk?>()
    private val sdkInfo = AtomicReference<SdkInfo>(SdkInfo.Unresolved)

    @TestOnly
    fun getSdkPromiseForTests() = sdk
    fun getSdkInfo(): SdkInfo = sdkInfo.get()
    fun getSdk(): Sdk? = if (getSdkInfo() is SdkInfo.Resolved) sdk.get() else null
    fun blockingGetSdk(): Sdk? = sdk.get()

    fun setSdkInfo(sdk: Sdk?) {
      when (sdk) {
        null -> sdkInfo.set(SdkInfo.Unresolved)
        else -> sdkInfo.set(SdkInfo.Resolving(sdk.name, sdk.versionString, sdk.homePath))
      }
    }

    fun setSdk(sdk: Sdk?) {
      when (sdk) {
        null -> sdkInfo.set(SdkInfo.Undefined)
        else -> sdkInfo.set(SdkInfo.Resolved(sdk.name, sdk.versionString, sdk.homePath))
      }
      this.sdk.setResult(sdk)
    }
  }
}
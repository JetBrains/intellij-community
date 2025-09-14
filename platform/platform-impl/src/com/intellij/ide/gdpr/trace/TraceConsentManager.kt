// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.trace

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to check whether the current IDE instance can operate with TRACE consent.
 */
@ApiStatus.Internal
interface TraceConsentManager {

  fun canCollectTraceConsent(): Boolean

  fun canDisplayTraceConsent(): Boolean

  fun isTraceConsentEnabled(): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TraceConsentManager> = ExtensionPointName("com.intellij.ide.gdpr.traceConsentManager")

    @JvmStatic
    fun getInstance(): TraceConsentManager? {
      return EP_NAME.findFirstSafe {
        getPluginInfo(it.javaClass).isDevelopedByJetBrains()
      }
    }
  }
}
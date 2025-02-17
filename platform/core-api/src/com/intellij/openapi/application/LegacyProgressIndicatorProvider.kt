// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

/**
 * An abstraction over the legacy API of progress indicators.
 * It needs to be used inside locking facilities without an explicit dependency on application part of the platform
 */
@ApiStatus.Internal
interface LegacyProgressIndicatorProvider {
  fun obtainProgressIndicator(): LegacyProgressIndicator?

  interface LegacyProgressIndicator {
    fun checkCanceled()
  }
}

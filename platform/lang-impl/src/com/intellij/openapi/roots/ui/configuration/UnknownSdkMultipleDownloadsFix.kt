// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UnknownSdkMultipleDownloadsFix<T>: UnknownSdkDownloadableSdkFix {
  /**
   * Shows UI to let the user pick a download.
   * @return true if the user selected a download option, false if they canceled the downloading intent
   */
  fun chooseItem(sdkTypeName : @NlsSafe String): Boolean
}

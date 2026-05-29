// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import java.nio.file.Path

/**
 * Local paths have no postfix, remote paths have.
 */
internal fun suggestSdkNamePostfix(sdkDownloadPath: Path): String? = sdkDownloadPath.getEelDescriptor().let { descriptor ->
  if (descriptor == localEel.descriptor) {
    null
  }
  else {
    descriptor.name
  }
}


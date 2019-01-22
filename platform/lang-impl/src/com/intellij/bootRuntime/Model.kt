// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.bundles.Remote
import com.intellij.bootRuntime.bundles.Runtime

enum class BundleState {
  REMOTE,
  DOWNLOADED,
  EXTRACTED,
  INSTALLED;

  fun getRepresentaton(): String {
    return when {
      this == REMOTE -> "Remote"
      this == DOWNLOADED -> "Downloaded"
      this == EXTRACTED -> "Extracted"
      this == INSTALLED -> "Inst"
      else -> "Unknown"
    }
  }
}

class Model(var selectedBundle: Runtime, val bundles:List<Runtime>) {

  fun updateBundle(newBundle:Runtime) {
    selectedBundle = newBundle
  }

  fun currentState () : BundleState {
     return when {
       isInstalled(selectedBundle) -> BundleState.INSTALLED
       isExtracted(selectedBundle) -> BundleState.EXTRACTED
       isDownloaded(selectedBundle) -> BundleState.DOWNLOADED
       isRemote(selectedBundle) -> BundleState.REMOTE
       else -> throw IllegalStateException()
     }
  }

  fun isInstalled(bundle:Runtime):Boolean = bundle.installationPath.exists()

  fun isExtracted(bundle:Runtime):Boolean = bundle.transitionPath.exists()

  fun isDownloaded(bundle:Runtime):Boolean = bundle.downloadPath.exists()

  fun isRemote(bundle:Runtime):Boolean = bundle is Remote
}

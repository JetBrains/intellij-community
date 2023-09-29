// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

/**
 * This class is to solve technical problem: you cannot obtain ScanningRequestToken from EDT, because
 * obtaining a token may trigger fingerprint calculation. FutureScanningRequestToken is EDT-safe.
 */
class FutureScanningRequestToken {
  @Volatile
  private var successful = false

  fun markSuccessful() {
    successful = true
  }

  fun isSuccessful() = successful
}
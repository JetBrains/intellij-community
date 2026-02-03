// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

data class SlowIndexedFile(
  val fileName: String,
  val processingTime: TimeNano,
  val evaluationOfIndexValueChangerTime: TimeNano,
  val contentLoadingTime: TimeNano
)
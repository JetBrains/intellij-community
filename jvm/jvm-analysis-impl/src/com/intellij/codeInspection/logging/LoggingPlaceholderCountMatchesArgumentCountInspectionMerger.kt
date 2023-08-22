// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.codeInspection.ex.InspectionElementsMergerBase

class LoggingPlaceholderCountMatchesArgumentCountInspectionMerger : InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "LoggingPlaceholderCountMatchesArgumentCount"

  override fun getSourceToolNames(): Array<String> = arrayOf("PlaceholderCountMatchesArgumentCount", "KotlinPlaceholderCountMatchesArgumentCount")
}
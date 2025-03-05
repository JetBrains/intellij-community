// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics



interface UserFactorDescription<out U : FactorUpdater, out R : FactorReader> {
  val factorId: String
  val updaterFactory: (MutableDoubleFactor) -> U
  val readerFactory: (DailyAggregatedDoubleFactor) -> R
}
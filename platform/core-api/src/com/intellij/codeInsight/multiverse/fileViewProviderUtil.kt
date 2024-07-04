// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("FileViewProviderUtil")
package com.intellij.codeInsight.multiverse

import com.intellij.concurrency.currentThreadContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

val currentCodeInsightSession: CodeInsightSession?
  @ApiStatus.Internal
  get() = currentThreadContext()[CodeInsightSessionElement]?.codeInsightSession

val currentCodeInsightContext: CodeInsightContext
  @ApiStatus.Internal
  get() = currentCodeInsightSession?.context ?: anyContext()


@ApiStatus.Internal
class CodeInsightSessionElement(val codeInsightSession: CodeInsightSession) : AbstractCoroutineContextElement(CodeInsightSessionElement) {
  companion object : CoroutineContext.Key<CodeInsightSessionElement>
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import org.jetbrains.annotations.ApiStatus

/**
 * Represents a code insight session.
 *
 * @see CodeInsightContextManager
 * @see CodeInsightContext
 */
@ApiStatus.NonExtendable
interface CodeInsightSession {
  val context: CodeInsightContext
}
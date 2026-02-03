// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything

import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

/**
 * Internal implementation of Language that is used in "Run Anything" popup. User for reporting statistics.
 */
@ApiStatus.Internal
object RunAnythingLanguage : Language("RunAnything"), DependentLanguage
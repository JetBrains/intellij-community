// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

/**
 * Internal implementation of Language that is used in "Search Everywhere" popup.
 * It is used for registering statistics relevant to actions invoked on top of "Search Everywhere" popup.
 */
@ApiStatus.Internal
object SearchEverywhereLanguage : Language("SearchEverywhere"), DependentLanguage
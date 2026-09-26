// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

/**
 * ID of an action or an action group registered in `plugin.xml`.
 *
 * DevKit resolves the literal passed to the constructor, so navigation, completion and find usages work
 * without `@Language` on every string. Pass [value] to an API that takes a string ID.
 */
@ApiStatus.Experimental
@JvmInline
value class ActionId(@Language("devkit-action-id") val value: String)

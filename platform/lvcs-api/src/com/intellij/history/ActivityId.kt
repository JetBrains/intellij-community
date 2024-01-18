// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Allows to identify which IDE activity created changes or labels in the local history.
 *
 * @property providerId identifier of the provider, responsible for the activity
 * @property kind additional information that can be used by the provider
 */
@ApiStatus.Experimental
data class ActivityId(val providerId: @NonNls String, val kind: @NonNls String)
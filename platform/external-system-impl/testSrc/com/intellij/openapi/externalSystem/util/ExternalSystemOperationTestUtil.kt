// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalSystemOperationTestUtil")

package com.intellij.openapi.externalSystem.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val DEFAULT_SYNC_TIMEOUT: Duration = 10.minutes
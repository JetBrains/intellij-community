// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.util.indexing.diagnostic

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

typealias TimeNano = Long
typealias TimeMillis = Long
typealias NumberOfBytes = Long

@TestOnly
var shouldDumpInUnitTestMode: Boolean = false

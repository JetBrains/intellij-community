// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.annotations.ApiStatus.NonExtendable

@NonExtendable
@Deprecated("Moved to com.intellij.platform.util.progress", level = DeprecationLevel.ERROR)
interface RawProgressReporter : RawProgressReporter

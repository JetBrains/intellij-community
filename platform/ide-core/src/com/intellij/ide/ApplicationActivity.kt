// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import org.jetbrains.annotations.ApiStatus

/**
 * Do not use this extension point.
 * Use specific extension points, lazy listeners, or services instead.
 *
 * This is an allowlist-only extension point.
 * It permits writing unsafe code with the risk of slowdown or application startup failures.
 *
 * Not executed in unit test mode.
*/
@ApiStatus.Internal
interface ApplicationActivity {
  suspend fun execute()
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import fleet.multiplatform.shims.currentThreadName
import fleet.util.logging.KLogger
import kotlinx.coroutines.CoroutineExceptionHandler

fun logExceptions(logger: KLogger) = logExceptions { logger }
fun logExceptions(logger: () -> KLogger) = CoroutineExceptionHandler { _, e ->
  logger().error(e) { "Exception in ${currentThreadName()}" }
}
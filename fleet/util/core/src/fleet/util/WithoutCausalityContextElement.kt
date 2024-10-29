// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend fun <T> withoutCausality(f: suspend CoroutineScope.() -> T): T {
  return withContext(WithoutCausalityContextElement, f)
}

object WithoutCausalityContextElement : CoroutineContext.Element,
                                        CoroutineContext.Key<WithoutCausalityContextElement> {
  override val key: CoroutineContext.Key<*> get() = this
}
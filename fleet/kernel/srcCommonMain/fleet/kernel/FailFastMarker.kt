// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import kotlin.coroutines.CoroutineContext

/**
 * Tests put it on top level coroutine context if they do not expect exceptions from the test body.
 *
 * Some exceptions are outside of our control, like IO.
 * Some, like IllegalArgumentException, are not supposed to happen,
 * yet production code will suppress them because they are not necessarily fatal.
 * It makes sense to fail a test that experiences the exception though.
 * If this is the case - check for presence of this element and change you strategy accordingly.
 *
 * For example:
 * - [fleet.kernel.rete.launchOnEach] will run coroutines on un-supervised scope
 *   and re-throw immediately if one of the matches fails.
 * - [fleet.kernel.plugins.PluginScope] will be created with Job instead of SupervisorJob,
 *   which means failure in any worker or action will terminate the application.
 * */
object FailFastMarker : CoroutineContext.Element, CoroutineContext.Key<FailFastMarker> {
  override val key: CoroutineContext.Key<*> get() = this
}

val CoroutineContext.shouldFailFast: Boolean
  get() = this[FailFastMarker] != null

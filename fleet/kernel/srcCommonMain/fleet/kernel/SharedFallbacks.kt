// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.ChangeScope

context(_: ChangeScope)
private fun <T> explicitShared(f: SharedChangeScope.() -> T) : T = shared(f)

context(_: ChangeScope)
private fun <T> explicitUnshared(f: SharedChangeScope.() -> T) : T = unshared(f)

/**
 * A stub for the `shared` with context(ChangeScope)
 */
fun <T> ChangeScope.shared(f: SharedChangeScope.() -> T): T = explicitShared(f)

/**
 * A stub for the `unshared` with context(ChangeScope); HACK
 */
fun <T> ChangeScope.unshared(f: SharedChangeScope.() -> T): T = explicitUnshared(f)
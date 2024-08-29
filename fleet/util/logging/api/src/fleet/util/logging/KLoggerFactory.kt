// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import kotlin.reflect.KClass

interface KLoggerFactory {
    fun logger(owner: KClass<*>): KLogger
    fun logger(owner: Class<*>): KLogger
    fun logger(owner: Any): KLogger
    fun logger(name: String): KLogger
}

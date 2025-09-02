// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.multiplatform.Actual
import java.util.*

@Actual("getLoggerFactory")
internal fun getLoggerFactoryJvm(): KLoggerFactory {
  return ServiceLoader.load(KLoggerFactory::class.java, KLoggerFactory::class.java.classLoader).first()
}
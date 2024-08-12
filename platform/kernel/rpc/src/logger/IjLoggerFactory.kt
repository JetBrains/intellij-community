// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.logger

import com.intellij.openapi.diagnostic.Logger
import fleet.util.logging.KLogger
import fleet.util.logging.KLoggerFactory
import kotlin.reflect.KClass

internal class IjLoggerFactory : KLoggerFactory {

  override fun logger(owner: KClass<*>): KLogger {
    return KLogger(IjLogger(Logger.getInstance(owner.java)))
  }

  override fun logger(owner: Class<*>): KLogger {
    return KLogger(IjLogger(Logger.getInstance(owner)))
  }

  override fun logger(owner: Any): KLogger {
    return KLogger(IjLogger(Logger.getInstance(owner.toString())))
  }

  override fun logger(name: String): KLogger {
    return KLogger(IjLogger(Logger.getInstance(name)))
  }
}

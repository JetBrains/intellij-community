// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator

import java.util.logging.Logger

object ProcessMediatorLogger {
  val LOG: Logger = Logger.getLogger("#" + ProcessMediatorLogger::class.java.name)
}

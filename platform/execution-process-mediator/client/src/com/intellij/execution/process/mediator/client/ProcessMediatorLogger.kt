// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.client

import java.util.logging.Logger

object ProcessMediatorLogger {
  val LOG: Logger = Logger.getLogger("#" + ProcessMediatorLogger::class.java.name)
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable


fun <R> runReadAction(action: () -> R): R {
  return ApplicationManager.getApplication()
    .runReadAction(ThrowableComputable { action() })
}

fun <R> runWriteAction(action: () -> R): R {
  return ApplicationManager.getApplication()
    .runWriteAction(ThrowableComputable { action() })
}

fun <R> runWriteActionAndGet(action: () -> R): R {
  return invokeAndWaitIfNeeded {
    runWriteAction {
      action()
    }
  }
}

fun runWriteActionAndWait(action: ThrowableRunnable<*>) {
  invokeAndWaitIfNeeded {
    runWriteAction {
      action.run()
    }
  }
}

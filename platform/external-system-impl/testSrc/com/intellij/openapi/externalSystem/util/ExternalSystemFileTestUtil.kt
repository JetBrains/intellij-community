// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.ThrowableComputable

fun <R> runReadAction(action: () -> R): R {
  return ApplicationManager.getApplication().runReadAction(ThrowableComputable(action))
}

fun <R> runWriteActionAndGet(action: () -> R): R {
  return invokeAndWaitIfNeeded {
    ApplicationManager.getApplication().runWriteAction(ThrowableComputable(action))
  }
}

fun runWriteActionAndWait(action: () -> Unit) {
  ApplicationManager.getApplication().invokeAndWait {
    ApplicationManager.getApplication().runWriteAction(action)
  }
}
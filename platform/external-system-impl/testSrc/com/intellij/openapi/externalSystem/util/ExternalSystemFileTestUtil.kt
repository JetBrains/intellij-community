// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.file.VirtualFileUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.testFramework.runInEdtAndGet as runInEdtAndGetImpl
import com.intellij.testFramework.runInEdtAndWait as runInEdtAndWaitImpl
import com.intellij.util.ui.UIUtil

val VirtualFile.textContent: String
  get() = VirtualFileUtil.getTextContent(this)

val VirtualFile.binaryContent: ByteArray
  get() = VirtualFileUtil.getBinaryContent(this)

fun VirtualFile.refreshAndWait() {
  runWriteActionAndWait {
    refresh(false, true)
  }
  runInEdtAndWait {
    UIUtil.dispatchAllInvocationEvents()
  }
}

fun <R> runReadAction(action: () -> R): R {
  return ApplicationManager.getApplication()
    .runReadAction(ThrowableComputable { action() })
}

fun <R> runWriteAction(action: () -> R): R {
  return ApplicationManager.getApplication()
    .runWriteAction(ThrowableComputable { action() })
}

fun <R> runWriteActionAndGet(action: () -> R): R {
  return runInEdtAndGet {
    runWriteAction {
      action()
    }
  }
}

fun runWriteActionAndWait(action: ThrowableRunnable<*>) {
  runInEdtAndWait {
    runWriteAction {
      action.run()
    }
  }
}

fun <R> runInEdtAndGet(action: () -> R): R {
  try {
    return runInEdtAndGetImpl {
      action()
    }
  }
  catch (e: Throwable) {
    throw Throwable(e)
  }
}

fun runInEdtAndWait(action: ThrowableRunnable<*>) {
  try {
    runInEdtAndWaitImpl {
      action.run()
    }
  }
  catch (e: Throwable) {
    throw Throwable(e)
  }
}

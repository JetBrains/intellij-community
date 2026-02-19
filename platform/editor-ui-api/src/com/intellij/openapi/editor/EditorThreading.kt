// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Historically, Editor models required read lock for reading them.
 * We are planning to relax the contracts and allow interacting with Editor from EDT without any locks.
 * Hence, editor models should use this class to ensure consistency.
 */
@ApiStatus.Experimental
interface EditorThreading {
  companion object {

    /**
     * Checks that interaction with the editor is allowed in the current context
     */
    @JvmStatic
    fun assertInteractionAllowed(): Unit = ApplicationManager.getApplication().service<EditorThreading>().doAssertInteractionAllowed()

    /**
     * Adjust the context for editor accessing operation [action].
     * [action] is always executed in place.
     */
    @JvmStatic
    fun <T, E : Throwable> compute(action: ThrowableComputable<T, E>): T = ApplicationManager.getApplication().service<EditorThreading>().doCompute(action)


    /**
     * Adjust the context for editor accessing operation [action].
     * [action] is always executed in place.
     */
    @JvmStatic
    fun run(action: Runnable): Unit = ApplicationManager.getApplication().service<EditorThreading>().doRun(action)

    @Internal
    @JvmStatic
    fun <T, E : Throwable> computeWritable(action: ThrowableComputable<T, E>): T = ApplicationManager.getApplication().service<EditorThreading>().doComputeWritable(action)

    @Internal
    @JvmStatic
    fun runWritable(action: Runnable): Unit = ApplicationManager.getApplication().service<EditorThreading>().doRunWritable(action)

    @Internal
    @JvmStatic
    fun assertWriteAllowed(): Unit = ApplicationManager.getApplication().service<EditorThreading>().doAssertWriteAllowed()

    @Internal
    @JvmStatic
    fun write(action: Runnable): Unit = ApplicationManager.getApplication().service<EditorThreading>().doWrite(action)
  }

  fun doAssertInteractionAllowed()

  fun <T, E : Throwable> doCompute(action: ThrowableComputable<T, E>): T

  fun doRun(action: Runnable)

  @Internal
  fun <T, E : Throwable> doComputeWritable(action: ThrowableComputable<T, E>): T

  @Internal
  fun doRunWritable(action: Runnable)

  @Internal
  fun doAssertWriteAllowed()

  @Internal
  fun doWrite(action: Runnable)
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Service(Service.Level.APP)
class JsonSchemaCacheManager {

  fun computeSchemaObject(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile): JsonSchemaObject? {
    val created: CompletableFuture<JsonSchemaObject?> = MyCompletableFuture()
    val future: CompletableFuture<JsonSchemaObject?> = getUpToDateFuture(schemaVirtualFile, schemaPsiFile, created)
    if (future === created) {
      start(schemaVirtualFile, schemaPsiFile, created)
    }
    return ApplicationUtil.runWithCheckCanceled(future, EmptyProgressIndicator.notNullize(ProgressManager.getInstance().progressIndicator))
  }

  private fun getUpToDateFuture(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile, created: CompletableFuture<JsonSchemaObject?>): CompletableFuture<JsonSchemaObject?> {
    synchronized(this) {
      val virtualFileModStamp: Long = schemaVirtualFile.modificationStamp
      val psiModStamp: Long = schemaPsiFile.modificationStamp
      val data: CachedData? = schemaVirtualFile.getUserData(MY_KEY)
      if (data != null && data.virtualModStamp == virtualFileModStamp && data.psiModStamp == psiModStamp) {
        return data.t
      }
      schemaVirtualFile.putUserData(MY_KEY, CachedData(created, virtualFileModStamp, psiModStamp))
      return created
    }
  }

  private data class CachedData(val t: CompletableFuture<JsonSchemaObject?>, val virtualModStamp: Long, val psiModStamp: Long)

  private fun start(schemaVirtualFile: VirtualFile,
                    schemaPsiFile: PsiFile,
                    future: CompletableFuture<JsonSchemaObject?>) {
    val promise = ReadAction.nonBlocking<JsonSchemaObject?> {
      JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile)
    }.submit(executor)
    promise.onSuccess {
      future.complete(it)
    }
    promise.onError {
      future.completeExceptionally(it)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance() : JsonSchemaCacheManager = service()
    private val MY_KEY = Key.create<CachedData>("CompletableFuture<JsonSchemaObjectCache>")
    private val executor: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("JSON Schema reader", 1)
  }
}

// To make FJ pool happy
// Bare CompletableFuture.get() may throw `RejectedExecutionException("Thread limit exceeded replacing blocked worker")`
// when FJ pool is full.
private class MyCompletableFuture<T> : CompletableFuture<T>() {
  override fun get(timeout: Long, unit: TimeUnit): T {
    val deadlineNano: Long = System.nanoTime() + unit.toNanos(timeout)
    try {
      return super.get(timeout, unit)
    }
    catch (e: RejectedExecutionException) {
      return getManually(deadlineNano)
    }
  }

  private fun getManually(deadlineNano: Long): T {
    while (System.nanoTime() < deadlineNano) {
      Thread.sleep(5)
      try {
        return super.get(max(0, deadlineNano - System.nanoTime()), TimeUnit.NANOSECONDS)
      }
      catch (_: RejectedExecutionException) {
      }
    }
    return super.get(0, TimeUnit.NANOSECONDS)
  }
}
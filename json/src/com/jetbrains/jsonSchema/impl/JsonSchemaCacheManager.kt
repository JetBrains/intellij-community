// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ScheduledExecutorService

@Service(Service.Level.PROJECT)
class JsonSchemaCacheManager : Disposable {

  private val cache: ConcurrentMap<VirtualFile, CachedValue<JsonSchemaObjectFuture>> = CollectionFactory.createConcurrentWeakMap()

  /**
   *  Computes [JsonSchemaObject] preventing multiple concurrent computations of the same schema.
   */
  fun computeSchemaObject(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile): JsonSchemaObject? {
    val newFuture: JsonSchemaObjectFuture = CompletableFuture()
    val future: JsonSchemaObjectFuture = getUpToDateFuture(schemaVirtualFile, schemaPsiFile, newFuture)
    if (future === newFuture) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        // Compute synchronously, because we can't start `NonBlockingReadAction` on EDT and
        // immediately after that wait on EDT for its computation in blocking manner.
        completeSync(schemaVirtualFile, schemaPsiFile, newFuture)
      }
      else {
        // We cannot `completeSync(schemaVirtualFile, schemaPsiFile, newFuture)` here, because of
        // unwanted `ProcessCanceledException` caching. If we try to avoid `ProcessCanceledException` caching by
        // starting a new computation, the new computation will fail with PCE too, because of cancelled progress.
        completeAsync(schemaVirtualFile, schemaPsiFile, newFuture)
      }
    }
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future, ProgressManager.getInstance().progressIndicator)
  }

  private fun getUpToDateFuture(schemaVirtualFile: VirtualFile,
                                schemaPsiFile: PsiFile,
                                newFuture: JsonSchemaObjectFuture): JsonSchemaObjectFuture {
    val cachedValue: CachedValue<JsonSchemaObjectFuture> = cache.compute(schemaVirtualFile) { _, prevValue ->
      val virtualFileModStamp: Long = schemaVirtualFile.modificationStamp
      val psiFileModStamp: Long = schemaPsiFile.modificationStamp
      if (prevValue != null && prevValue.virtualFileModStamp == virtualFileModStamp && prevValue.psiFileModStamp == psiFileModStamp) {
        prevValue
      }
      else {
        CachedValue(newFuture, virtualFileModStamp, psiFileModStamp)
      }
    }!! // !!, because`remappingFunction` always returns not-null value
    return cachedValue.value
  }

  private fun completeSync(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile, future: JsonSchemaObjectFuture) {
    try {
      future.complete(JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile))
    }
    catch (e: Exception) {
      completeExceptionally(future, e)
    }
  }

  private fun completeAsync(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile, future: JsonSchemaObjectFuture) {
    val promise = ReadAction.nonBlocking<JsonSchemaObject?> {
      JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile)
    }.expireWith(this).submit(READER_EXECUTOR)
    promise.onSuccess {
      future.complete(it)
    }
    promise.onError {
      completeExceptionally(future, it)
    }
  }

  private fun completeExceptionally(future: CompletableFuture<*>, e: Throwable) {
    if (e is ProcessCanceledException) {
      thisLogger().error("PCE will be cached unexpectedly", e)
    }
    future.completeExceptionally(e)
  }

  override fun dispose() {
    cache.clear()
  }

  private data class CachedValue<T>(val value: T, val virtualFileModStamp: Long, val psiFileModStamp: Long)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JsonSchemaCacheManager = project.service()
    private val READER_EXECUTOR: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("JSON Schema reader", 1)
  }
}

private typealias JsonSchemaObjectFuture = CompletableFuture<JsonSchemaObject?>

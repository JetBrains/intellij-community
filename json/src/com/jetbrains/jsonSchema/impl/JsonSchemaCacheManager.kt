// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
class JsonSchemaCacheManager : Disposable {

  private val cache: ConcurrentMap<VirtualFile, CachedValue<JsonSchemaObjectFuture>> = CollectionFactory.createConcurrentWeakMap()

  /**
   *  Computes [JsonSchemaObject] preventing multiple concurrent computations of the same schema.
   */
  fun computeSchemaObject(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile): JsonSchemaObject? {
    if (Registry.`is`("json.schema.object.v2")) {
      assert(false) {
        "Should not use cache with the new json object impl"
      }
    }
    val newFuture: JsonSchemaObjectFuture = CompletableFuture()
    val cachedValue: CachedValue<JsonSchemaObjectFuture> = getUpToDateFuture(schemaVirtualFile, schemaPsiFile, newFuture)
    val cachedFuture = cachedValue.value
    if (cachedFuture === newFuture) {
      completeSync(schemaVirtualFile, schemaPsiFile, cachedFuture)
    }
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(cachedFuture, ProgressManager.getInstance().progressIndicator)
    }
    catch (e: ProcessCanceledException) {
      ProgressManager.checkCanceled() // rethrow PCE if this thread's progress is cancelled

      // `ProgressManager.checkCanceled()` was passed => the thread's progress is not cancelled
      // => PCE was thrown because of `cachedFuture.completeExceptionally(PCE)`
      // => evict cached PCE and re-compute the schema
      cache.remove(schemaVirtualFile, cachedValue)

      // The recursion shouldn't happen more than once:
      // Now, the thread's progress is not cancelled.
      // If on the second `computeSchemaObject(...)` call we happened to catch PCE again, then
      // a write action has started => `ProgressManager.checkCanceled()` should throw PCE
      // preventing the third `computeSchemaObject(...)` call.
      return computeSchemaObject(schemaVirtualFile, schemaPsiFile)
    }
  }

  private fun getUpToDateFuture(schemaVirtualFile: VirtualFile,
                                schemaPsiFile: PsiFile,
                                newFuture: JsonSchemaObjectFuture): CachedValue<JsonSchemaObjectFuture> {
    return cache.compute(schemaVirtualFile) { _, prevValue ->
      val virtualFileModStamp: Long = schemaVirtualFile.modificationStamp
      val psiFileModStamp: Long = schemaPsiFile.modificationStamp
      if (prevValue != null && prevValue.virtualFileModStamp == virtualFileModStamp && prevValue.psiFileModStamp == psiFileModStamp) {
        prevValue
      }
      else {
        CachedValue(newFuture, virtualFileModStamp, psiFileModStamp)
      }
    }!! // !!, because`remappingFunction` always returns not-null value
  }

  private fun completeSync(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile, future: JsonSchemaObjectFuture) {
    try {
      future.complete(JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile))
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
  }

  override fun dispose() {
    cache.clear()
  }

  private data class CachedValue<T>(val value: T, val virtualFileModStamp: Long, val psiFileModStamp: Long)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JsonSchemaCacheManager = project.service()
  }
}

private typealias JsonSchemaObjectFuture = CompletableFuture<JsonSchemaObject?>

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.lang.ref.SoftReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService

@Service(Service.Level.PROJECT)
class JsonSchemaCacheManager : Disposable {

  fun computeSchemaObject(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile): JsonSchemaObject? {
    val created: CompletableFuture<JsonSchemaObjectRef> = CompletableFuture()
    val future: CompletableFuture<JsonSchemaObjectRef> = getUpToDateValue(schemaVirtualFile, schemaPsiFile, created, CACHE_KEY)
    if (future === created) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        completeSync(schemaVirtualFile, schemaPsiFile, created)
      }
      else {
        completeAsync(schemaVirtualFile, schemaPsiFile, created)
      }
    }
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future, ProgressManager.getInstance().progressIndicator).get()
  }

  private fun completeSync(schemaVirtualFile: VirtualFile,
                           schemaPsiFile: PsiFile,
                           future: CompletableFuture<JsonSchemaObjectRef>) {
    try {
      future.complete(SoftReference(JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile)))
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
  }

  private fun completeAsync(schemaVirtualFile: VirtualFile,
                            schemaPsiFile: PsiFile,
                            future: CompletableFuture<JsonSchemaObjectRef>) {
    val promise = ReadAction.nonBlocking<JsonSchemaObjectRef> {
      SoftReference(JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile))
    }.expireWith(this).submit(READER_EXECUTOR)
    promise.onSuccess {
      future.complete(it)
    }
    promise.onError {
      future.completeExceptionally(it)
    }
  }

  override fun dispose() {
  }

  private data class CachedValue<T>(val value: T, val virtualModStamp: Long, val psiModStamp: Long)

  companion object {

    @JvmStatic
    fun getInstance(project: Project): JsonSchemaCacheManager = project.service()
    private val CACHE_KEY = Key.create<CachedValue<CompletableFuture<JsonSchemaObjectRef>>>("Future<JsonSchemaObjectCache>")
    private val READER_EXECUTOR: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("JSON Schema reader", 1)

    @Suppress("SameParameterValue")
    private fun <T> getUpToDateValue(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile, created: T, key: Key<CachedValue<T>>): T {
      synchronized(this) {
        val virtualFileModStamp: Long = schemaVirtualFile.modificationStamp
        val psiModStamp: Long = schemaPsiFile.modificationStamp
        val data: CachedValue<T>? = schemaVirtualFile.getUserData(key)
        if (data != null && data.virtualModStamp == virtualFileModStamp && data.psiModStamp == psiModStamp) {
          return data.value
        }
        schemaVirtualFile.putUserData(key, CachedValue(created, virtualFileModStamp, psiModStamp))
        return created
      }
    }
  }
}

private typealias JsonSchemaObjectRef = SoftReference<JsonSchemaObject?>

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AtomicMapCache
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer

/**
 * Stores mapping (file -> FileProviderMap(context -> Weak(FileViewProvider)))
 */
internal class MultiverseFileViewProviderCache(
  private val project: Project,
  private val newFileViewProviderFactory: NewFileViewProviderFactory,
) : FileViewProviderCache {

  private val myTempProviderStorage = createTemporaryProviderStorage()
  private val evaluator: ValidityEvaluator = ValidityEvaluatorImpl(myTempProviderStorage, this, newFileViewProviderFactory)
  private val synchronizer = CancellableSynchronizer()

  // todo IJPL-339 we need atomic update for values of the maps???

  // todo IJPL-339 don't store map for a single item
  private val cache = AtomicMapCache<VirtualFile, FileProviderMap> {
    CollectionFactory.createConcurrentWeakValueMap()
  }

  // todo IJPL-339 do clear only under write lock
  override fun clear() {
    log.doTrace { "clear cache" }
    cache.invalidate()
  }

  // todo IJPL-339 do read only under read lock
  override fun cacheOrGet(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider): FileViewProvider {
    log.doTrace { "cacheOrGet $file $context $provider" }
    val map = getFileProviderMap(file)
    val effectiveViewProvider = map.cacheOrGet(context, provider)
    log.doTrace { "cacheOrGet finished $file $context $provider, effectiveProvider=$effectiveViewProvider" }
    return effectiveViewProvider
  }

  private fun getFileProviderMap(file: VirtualFile): FileProviderMap {
    return cache.getOrPut(file) { FileProviderMap() }
  }

  override fun forEachKey(block: Consumer<VirtualFile>) {
    doIfInitialized { map ->
      map.keys.forEach(block)
    }
  }

  override fun forEach(block: FileViewProviderCache.CacheEntryConsumer) {
    doIfInitialized { map ->
      map.forEach { (file, map) ->
        map.forEach { context, provider ->
          block.consume(file, context, provider)
        }
      }
    }
  }

  override fun getAllProvidersRaw(vFile: VirtualFile): List<FileViewProvider> =
    cache[vFile]?.allProviders ?: emptyList()

  override fun getAllProvidersAndReanimateIfNecessary(vFile: VirtualFile): List<FileViewProvider> {
    val fileMap = cache[vFile] ?: return emptyList()
    val reanimated = reanimateIfNecessary(vFile, fileMap, null) ?: return emptyList()
    return reanimated.allProviders
  }

  override fun getRaw(file: VirtualFile, context: CodeInsightContext): FileViewProvider? =
    cache[file]?.get(context)

  override fun getAndReanimateIfNecessary(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider? {
    val fileMap = cache[vFile] ?: return null
    val reanimated = reanimateIfNecessary(vFile, fileMap, context) ?: return null
    return reanimated[context]
  }

  @RequiresReadLock
  private fun reanimateIfNecessary(
    vFile: VirtualFile,
    fileMap: FileProviderMap,
    context: CodeInsightContext?,
  ): FileProviderMap? {
    if (!fileMap.isPossiblyInvalidated) return fileMap

    return synchronizer.cancellableSynchronized(fileMap) {
      if (!fileMap.isPossiblyInvalidated) {
        return@cancellableSynchronized cache[vFile] // either same if survived in another thread, or null if died, or a new map if died and restored in another thread
      }

      evaluateValidityUnderLock(vFile, context, fileMap)
    }
  }

  private fun evaluateValidityUnderLock(
    vFile: VirtualFile,
    context: CodeInsightContext?,
    fileMap: FileProviderMap,
  ): FileProviderMap? {
    if (!vFile.isValid()) return null

    if (context != null) {
      val temp = myTempProviderStorage.get(vFile)
      if (temp != null) {    // todo check this if context == null
        val tempFileProviderMap = FileProviderMap()
        tempFileProviderMap.cacheOrGet(context, temp)
        return tempFileProviderMap
      }
    }

    val firstEntry = fileMap.entries.firstOrNull()
    if (firstEntry == null ||
        !evaluator.isRecreatedViewProviderIsIdentical(vFile, firstEntry.value as AbstractFileViewProvider, firstEntry.key)
    ) {
      dropPossibleInvalidation(fileMap)
      remove(vFile)
      return null
    }

    val contextMapping = reassignProvidersWithOutdatedContextToActualContexts(vFile, fileMap)
    if (contextMapping.isNotEmpty()) {
      SmartPointerManagerEx.getInstanceEx(project).getTracker(vFile)?.pushContextMapping(contextMapping)
    }

    dropPossibleInvalidation(fileMap)

    return fileMap
  }

  private fun dropPossibleInvalidation(fileMap: FileProviderMap) {
    fileMap.forEach { _, provider ->
      provider.unmarkPossiblyInvalidated()
    }
    fileMap.isPossiblyInvalidated = false
  }

  private fun reassignProvidersWithOutdatedContextToActualContexts(
    vFile: VirtualFile,
    fileMap: FileProviderMap,
  ): Map<CodeInsightContext, CodeInsightContext?> {
    val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
    val actualContexts = contextManager.getCodeInsightContexts(vFile)

    val outdatedProviders = mutableListOf<Pair<FileViewProvider, CodeInsightContext>>()

    val actualUnusedContextSet = LinkedHashSet(actualContexts) // ordered!
    fileMap.forEach { context, provider ->
      if (actualContexts.contains(context)) {
        actualUnusedContextSet.remove(context)
      }
      else {
        outdatedProviders.add(provider to context)
      }
    }

    if (outdatedProviders.isEmpty()) {
      return emptyMap()
    }

    val contextMapping = mutableMapOf<CodeInsightContext, CodeInsightContext?>()

    outdatedProviders.zip(actualUnusedContextSet).forEach { (outdatedProviderAndContext, context) ->
      val (outdatedProvider, outDatedContext) = outdatedProviderAndContext
      contextMapping[outDatedContext] = context
      fileMap.remove(outDatedContext, outdatedProvider)
      contextManager.setCodeInsightContext(outdatedProvider, context)
      require(fileMap.cacheOrGet(context, outdatedProvider) == outdatedProvider)
    }

    if (actualUnusedContextSet.size < outdatedProviders.size) {
      outdatedProviders.subList(actualUnusedContextSet.size, outdatedProviders.size).forEach { (outdatedProvider, context) ->
        contextMapping[context] = null
        fileMap.remove(context, outdatedProvider)
        outdatedProvider.unmarkPossiblyInvalidated()
      }
    }

    return contextMapping
  }

  override fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider) {
    log.doTrace { "removeAllAndSetAny $vFile $viewProvider" }
    val fileMap = getFileProviderMap(vFile)
    fileMap.removeAllAndSetAny(viewProvider)
  }

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? {
    log.doTrace { "remove $file" }
    val cacheMap = cache.getCacheIfInitialized() ?: return null
    val map = cacheMap.remove(file) ?: return null
    return map.entries.asSequence().map { it.value }.asIterable()
  }

  /**
   * Removes cached value for ([file], [context]) pair only if the cached value equals [viewProvider]
   */
  override fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean {
    log.doTrace { "remove $file $context $viewProvider" }
    val result = cache.getCacheIfInitialized()?.get(file)?.remove(context, viewProvider) == true
    log.doTrace { "remove finished $file $context $viewProvider, result=$result" }
    return result
  }

  override fun processQueue() {
    doIfInitialized { map ->
      // cache.cache is in fact ConcurrentWeakValueHashMap.
      // calling cache.cache.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
      map.remove(NullFile)
    }
  }

  private fun doIfInitialized(block: (FullCacheMap) -> Unit) {
    val map = cache.getCacheIfInitialized() ?: return
    block(map)
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    log.doTrace { "trySetContext $viewProvider $context" }
    val vFile = viewProvider.virtualFile
    val map = getFileProviderMap(vFile)
    val effectiveContext = map.trySetContext(viewProvider, context)
    log.doTrace { "trySetContext finished $viewProvider $context, effectiveContext=$effectiveContext" }
    return effectiveContext
  }

  @RequiresWriteLock
  override fun markPossiblyInvalidated() {
    SmartPointerManagerEx.getInstanceEx(project).possiblyInvalidationModCounter.incModificationCount()
    doIfInitialized { map ->
      map.forEach { (_, map: FileProviderMap?) ->
        if (map != null) {
          map.isPossiblyInvalidated = true
          map.forEach { _, provider ->
            provider.markPossiblyInvalidated()
          }
        }
      }
    }
  }

  override fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean {
    val vFile = viewProvider.virtualFile
    val fileMap = cache[vFile] ?: return false

    val reanimatedFileMap = reanimateIfNecessary(vFile, fileMap, viewProvider.getRawContext()) ?: return false

    return reanimatedFileMap[viewProvider.getRawContext()] === viewProvider
  }

  override fun findViewProvider(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider {
    getAndReanimateIfNecessary(vFile, context)?.let {
      return it
    }

    myTempProviderStorage.get(vFile)?.let {
      return it
    }

    val viewProvider = newFileViewProviderFactory.createNewFileViewProvider(vFile, context)

    return cacheOrGet(vFile, context, viewProvider)
  }
}

private inline fun Logger.doTrace(block: () -> String) {
  if (!isTraceEnabled) return

  val message = block()
  if (stacktraceOnTraceLevelEnabled.get()) {
    trace(Throwable(message))
  }
  else {
    trace(message)
  }
}

private val stacktraceOnTraceLevelEnabled = AtomicBoolean(false)

@ApiStatus.Internal
object MultiverseFileViewProviderCacheLog {
  /**
   * Works only if [log] level is set to `TRACE`.
   * Use only when really necessary because it is rather expensive.
   */
  @JvmStatic
  fun enableStacktraceOnTraceLevel(disposable: Disposable) {
    val prevValue = stacktraceOnTraceLevelEnabled.getAndSet(true)
    if (prevValue) return // no need to revert value after use

    Disposer.register(disposable) {
      stacktraceOnTraceLevelEnabled.set(false)
    }
  }
}

private object NullFile : LightVirtualFile()

private typealias FullCacheMap = ConcurrentMap<VirtualFile, FileProviderMap>

private val log = logger<MultiverseFileViewProviderCache>()

private fun FileViewProvider.getRawContext(): CodeInsightContext =
  CodeInsightContextManagerImpl.getInstanceImpl(this.manager.project).getCodeInsightContextRaw(this)

private class CancellableSynchronizer {
  private val lockMap = CollectionFactory.createConcurrentWeakValueMap<FileProviderMap, ReentrantLock>()

  fun <T> cancellableSynchronized(fileMap: FileProviderMap, block: () -> T): T {
    val lock = lockMap.computeIfAbsent(fileMap) { ReentrantLock() }
    lock.awaitWithCheckCanceled()
    try {
      return block()
    }
    finally {
      lock.unlock()
    }
  }
}
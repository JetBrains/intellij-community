// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.FileViewProvider
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.util.AtomicMapCache
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class CodeInsightContextManagerImpl(
  private val project: Project,
  private val cs: CoroutineScope,
) : CodeInsightContextManager, Disposable.Default {

  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): CodeInsightContextManagerImpl =
      CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
  }

  private val allContexts: AtomicMapCache<VirtualFile, ContextOrArray, ConcurrentMap<VirtualFile, ContextOrArray>> =
    AtomicMapCache { CollectionFactory.createConcurrentWeakKeySoftValueMap() }

  private val preferredContext: AtomicMapCache<VirtualFile, CodeInsightContext, ConcurrentMap<VirtualFile, CodeInsightContext>> =
    AtomicMapCache { CollectionFactory.createConcurrentWeakKeySoftValueMap() }

  private val _changeFlow = MutableSharedFlow<Unit>()

  /** invalidation job needs to be canceled and recreated on extension point update */
  @Volatile
  private var invalidationProcessorJob: Job? = null

  private fun invalidateAllContexts() {
    // it's unnecessary here to serialize invalidation requests because they are all equal, and it's unimportant, which is called first.
    // once more granular invalidation requests are added, it's necessary to add serialization (e.g., via a flow)
    cs.launch {
      edtWriteAction {
        preferredContext.invalidate()
        allContexts.invalidate()

        project.messageBus.syncPublisher(CodeInsightContextManager.topic).contextsChanged()
        log.trace { "all contexts are invalidated" }
      }
      _changeFlow.emit(Unit)
    }
  }

  init {
    EP_NAME.addChangeListener(cs, Runnable {
      cs.launch {
        subscribeToChanges()
        invalidateAllContexts()
      }
    })
    subscribeToChanges()
    InvalidationBulkFileListener.subscribeToVfsEvents()
  }

  private fun subscribeToChanges() {
    invalidationProcessorJob?.cancel()
    invalidationProcessorJob = cs.launch {
      EP_NAME.extensionList.map { it.invalidationRequestFlow(project) }.merge().collectLatest {
        invalidateAllContexts()
      }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun getCodeInsightContexts(file: VirtualFile): List<CodeInsightContext> {
    // FIXME: the assert had never worked due to IJPL-221633, but when it is enabled some tests fail
    // ThreadingAssertions.softAssertBackgroundThread()
    ThreadingAssertions.softAssertReadAccess()

    if (!isSharedSourceSupportEnabled(project)) return listOf(defaultContext())

    return allContexts.getOrPut(file) {
      log.trace { "requested all contexts of file ${file.path}" }
      getContextSequence(file).toContextOrArray()
    }.wrapToList()
  }

  private fun getContextSequence(file: VirtualFile): Sequence<CodeInsightContext> {
    if (!isSharedSourceSupportEnabled(project)) return emptySequence()

    return EP_NAME.lazySequence().flatMap { provider ->
      provider.getContextSafely(file)
    }.appendIfEmpty(defaultContext())
  }

  override val changeFlow: Flow<Unit> = _changeFlow.asSharedFlow()

  @RequiresBackgroundThread
  @RequiresReadLock
  override fun getPreferredContext(file: VirtualFile): CodeInsightContext {
    if (!isSharedSourceSupportEnabled(project)) return defaultContext()

    // FIXME: the assert had never worked due to IJPL-221633, but when it is enabled some tests fail
    // ThreadingAssertions.softAssertBackgroundThread()
    ThreadingAssertions.softAssertReadAccess()

    log.trace { "requested preferred context of file ${file.path}" }

    return preferredContext.getOrPut(file) {
      findFirstContext(file).also { log.assertTrue(it !== anyContext()) { "preferredContext must not be anyContext" } }
    }
  }

  override fun getCodeInsightContext(fileViewProvider: FileViewProvider): CodeInsightContext {
    if (!isSharedSourceSupportEnabled(project)) return defaultContext()

    log.trace { "requested context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    // FIXME: the assert had never worked due to IJPL-221633, but when it is enabled some tests fail
    // ThreadingAssertions.softAssertBackgroundThread()
    ThreadingAssertions.softAssertReadAccess()

    val context = getCodeInsightContextRaw(fileViewProvider)

    if (context == anyContext()) {
      return inferContext(fileViewProvider)
    }

    return context
  }

  private fun findFirstContext(file: VirtualFile?): CodeInsightContext {
    if (file == null) return defaultContext()

    // todo IJPL-339 implement a better way to select the current context
    val firstContext = getContextSequence(file).first()
    return firstContext
  }


  private fun CodeInsightContextProvider.getContextSafely(file: VirtualFile): List<CodeInsightContext> {
    return runSafely { this.getContexts(file, project) } ?: emptyList()
  }

  private inline fun <T : Any> runSafely(block: () -> T): T? {
    try {
      return block()
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      log.error(e)
      return null
    }
  }

  private fun inferContext(fileViewProvider: FileViewProvider): CodeInsightContext {
    log.trace { "infer context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    val preferredContext = getPreferredContext(fileViewProvider.virtualFile)

    val setContext = trySetContext(fileViewProvider, preferredContext)

    log.trace { "context of FileViewProvider ${fileViewProvider.virtualFile.path} is set to $setContext" }

    return setContext
  }

  @RequiresReadLock
  private fun trySetContext(
    fileViewProvider: FileViewProvider,
    context: CodeInsightContext,
  ): CodeInsightContext {
    val fileManager = PsiManagerEx.getInstanceEx(project).fileManagerEx

    // if the viewProvider is already stored in the fileManager, we need to update it there
    val result = fileManager.trySetContext(fileViewProvider, context)
    if (result != null) {
      return result
    }
    else {
      // result was null, thus fileViewProvider was not stored in the fileManager yet
      // we just need to install the context in the viewProvider
      setCodeInsightContext(fileViewProvider, context)
      return context
    }
  }

  override fun getCodeInsightContextRaw(fileViewProvider: FileViewProvider): CodeInsightContext =
    fileViewProvider.getUserData(codeInsightContextKey) ?: defaultContext()

  @RequiresReadLock
  fun setCodeInsightContext(fileViewProvider: FileViewProvider, context: CodeInsightContext) {
    log.trace { "set context of FileViewProvider ${fileViewProvider.virtualFile.path} to $context" }

    val effectiveContext = context.takeUnless { it == defaultContext() }
    fileViewProvider.putUserData(codeInsightContextKey, effectiveContext)
  }

  private class InvalidationBulkFileListener : BulkFileListenerBackgroundable {
    override fun before(events: List<VFileEvent>) {
      val moveEvents = events.filterIsInstance<VFileMoveEvent>().ifEmpty { return }

      val projectLocator = ProjectLocator.getInstance()
      val projects = moveEvents.mapNotNullTo(mutableSetOf()) { projectLocator.guessProjectForFile(it.file) }

      for (project in projects) {
        val manager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
        manager.preferredContext.invalidate()
        manager.allContexts.invalidate()
      }
    }

    companion object {
      private val subscribed = AtomicBoolean(false)

      fun subscribeToVfsEvents() {
        // we need only one listener per application
        if (!subscribed.getAndSet(true)) {
          ApplicationManager.getApplication().messageBus.connect()
            .subscribe(VirtualFileManager.VFS_CHANGES_BG, InvalidationBulkFileListener())
        }
      }
    }
  }
}

private val EP_NAME = ExtensionPointName.create<CodeInsightContextProvider>("com.intellij.multiverse.codeInsightContextProvider")

private val codeInsightContextKey = Key.create<CodeInsightContext>("codeInsightContextKey")

private val log = logger<CodeInsightContextManagerImpl>()

/**
 * appends an item to the sequence if the sequence is empty
 */
private fun <T> Sequence<T>.appendIfEmpty(item: T) = sequence {
  var isEmpty = true
  for (item in this@appendIfEmpty) {
    yield(item)
    isEmpty = false
  }
  if (isEmpty) {
    yield(item)
  }
}

/**
 * a single [CodeInsightContext] or an array of [CodeInsightContext]s
 */
private typealias ContextOrArray = Any

private fun ContextOrArray.wrapToList(): List<CodeInsightContext> {
  @Suppress("UNCHECKED_CAST")
  return when (this) {
    is Array<*> -> (this as Array<CodeInsightContext>).asList()
    else -> listOf(this as CodeInsightContext)
  }
}

private fun Sequence<CodeInsightContext>.toContextOrArray(): ContextOrArray {
  val iterator = this.iterator()
  if (!iterator.hasNext()) return emptyArray<CodeInsightContext>()

  val first = iterator.next()
  if (!iterator.hasNext()) return first

  val arrayList = ArrayList<CodeInsightContext>()
  arrayList.add(first)
  while (iterator.hasNext()) {
    arrayList.add(iterator.next())
  }
  return arrayList.toTypedArray()
}

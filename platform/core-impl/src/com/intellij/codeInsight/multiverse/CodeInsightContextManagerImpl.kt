// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EditorLockFreeTyping
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.diagnostic.debug
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
import com.intellij.util.concurrency.ThreadingAssertions.assertWriteAccess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
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

  private val allContexts: AtomicMapCache<VirtualFile, ContextOrArray> =
    AtomicMapCache { CollectionFactory.createConcurrentWeakKeySoftValueMap() }

  private val preferredContext: AtomicMapCache<VirtualFile, CodeInsightContext> =
    AtomicMapCache { CollectionFactory.createConcurrentWeakKeySoftValueMap() }

  private val _changeFlow = MutableSharedFlow<Unit>()

  init {
    EP_NAME.addChangeListener(cs) {
      cs.launch {
        subscribeToChanges()
        backgroundWriteAction {
          invalidateAllContexts()
        }
      }
    }
    subscribeToChanges()
    InvalidationBulkFileListener.subscribeToVfsEvents()
  }

  private fun subscribeToChanges() {
    val invalidator = { invalidateAllContexts() }
    EP_NAME.forEachExtensionSafe { provider ->
      provider.subscribeToChanges(project, invalidator)
    }
  }

  private fun invalidateAllContexts() {
    assertWriteAccess()
    preferredContext.invalidate()
    allContexts.invalidate()
    project.messageBus.syncPublisher(CodeInsightContextManager.topic).contextsChanged()
    _changeFlow.tryEmit(Unit)
    log.debug { "[ctx-diag] all contexts invalidated" }
    log.trace { "all contexts are invalidated" }
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
      getContextSequence(file).toContextOrArray().also {
        log.debug { "[ctx-diag] computed contexts of ${file.path}: ${it.wrapToList()}" }
      }
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
    if (EditorLockFreeTyping.isReadAccessNeeded(file)) {
      ThreadingAssertions.softAssertReadAccess()
    }

    log.trace { "requested preferred context of file ${file.path}" }

    return preferredContext.getOrPut(file) {
      val contexts = getCodeInsightContexts(file)
      // TODO IJPL-339 implement a better way to select the preferred context
      val preferred = contexts.first()
      log.assertTrue(preferred !== anyContext()) { "preferredContext must not be anyContext" }
      preferred
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

  @TestOnly
  override fun registerTestOnlyCodeInsightContextProvider(provider: CodeInsightContextProvider, disposable: Disposable) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      throw IllegalStateException("This method is only available in tests")
    }

    EP_NAME.point.registerExtension(provider, disposable)
  }


  private class InvalidationBulkFileListener : BulkFileListenerBackgroundable {
    override fun before(events: List<VFileEvent>) {
      val moveEvents = events.filterIsInstance<VFileMoveEvent>().ifEmpty { return }

      val projectLocator = ProjectLocator.getInstance()
      val projects = moveEvents.mapNotNullTo(mutableSetOf()) { projectLocator.guessProjectForFile(it.file) }

      for (project in projects) {
        val manager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
        manager.invalidateAllContexts()
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

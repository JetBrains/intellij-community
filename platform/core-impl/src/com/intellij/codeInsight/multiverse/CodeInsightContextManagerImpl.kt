// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
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
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AtomicMapCache
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.ThreadingAssertions.assertWriteAccess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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

  // The buffer lets tryEmit succeed when subscribers are present. A zero-capacity flow rejects tryEmit and drops the invalidation event.
  private val _changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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
    val emitted = _changeFlow.tryEmit(Unit)
    log.assertTrue(emitted, "failed to emit a context invalidation event, subscribers are not notified")
    log.debug { "[ctx-diag] all contexts invalidated" }
    log.trace { "all contexts are invalidated" }
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  override fun getCodeInsightContexts(file: VirtualFile): List<CodeInsightContext> {
    if (!isSharedSourceSupportEnabled(project)) return listOf(defaultContext())

    ensureReadAccess(file)

    return getContextsOf(file).wrapToList()
  }

  /** lazySequence, not extensionList: getContexts can cost a workspace-index query, so providers past the owner are not asked. */
  private fun getContextsOf(file: VirtualFile): ContextOrArray = allContexts.getOrPut(file) {
    log.trace { "requested all contexts of file ${file.path}" }

    for (provider in EP_NAME.lazySequence()) {
      val contexts = provider.getContextSafely(file) ?: continue
      return@getOrPut contexts.ifEmpty { listOf(defaultContext()) }.asSequence().toContextOrArray().also {
        log.debug { "[ctx-diag] computed contexts of ${file.path}: ${it.wrapToList()}" }
      }
    }

    defaultContext()
  }

  /** The provider whose contexts [file] carries. Derived from the contexts, so the cache stores no owner per file. */
  private fun findOwner(file: VirtualFile): CodeInsightContextProvider? {
    val context = getContextsOf(file).getFirstContextOrNull() ?: return null
    return EP_NAME.lazySequence().firstOrNull { runSafely { it.isOwnerOf(context) } == true }
  }

  override val changeFlow: Flow<Unit> = _changeFlow.asSharedFlow()

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  override fun getPreferredContext(file: VirtualFile): CodeInsightContext {
    if (!isSharedSourceSupportEnabled(project)) return defaultContext()

    ensureReadAccess(file)

    log.trace { "requested preferred context of file ${file.path}" }

    return preferredContext.getOrPut(file) {
      val preferred = selectPreferredContext(file)
      log.assertTrue(preferred !== anyContext()) { "preferredContext must not be anyContext" }
      preferred
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun <T> withOwnerOf(file: VirtualFile, block: (CodeInsightContextProvider) -> T): T? {
    if (!isSharedSourceSupportEnabled(project)) return null

    ensureReadAccess(file)

    val provider = findOwner(file) ?: return null
    return runSafely { block(provider) }
  }

  private fun selectPreferredContext(file: VirtualFile): CodeInsightContext {
    val contexts = getContextsOf(file).wrapToList()
    val owner = findOwner(file) ?: return contexts.first()

    val nominee = runSafely { owner.getPreferredContext(file, project, contexts) } ?: return contexts.first()
    if (nominee in contexts) return nominee

    log.warn("${owner.javaClass.name}.getPreferredContext returned $nominee, not among the contexts offered for ${file.path}")
    return contexts.first()
  }

  override fun getCodeInsightContext(fileViewProvider: FileViewProvider): CodeInsightContext {
    if (!isSharedSourceSupportEnabled(project)) return defaultContext()

    log.trace { "requested context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    ensureReadAccess(fileViewProvider.virtualFile)

    val context = getCodeInsightContextRaw(fileViewProvider)

    if (context == anyContext()) {
      return inferContext(fileViewProvider)
    }

    return context
  }

  /** `null` when the provider does not own [file], or when it threw. A provider that throws is broken, so we isolate it. */
  private fun CodeInsightContextProvider.getContextSafely(file: VirtualFile): List<CodeInsightContext>? {
    return runSafely { this.getContexts(file, project) }
  }

  private inline fun <T> runSafely(block: () -> T): T? {
    try {
      return block()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      log.error(e)
      return null
    }
  }

  private fun inferContext(fileViewProvider: FileViewProvider): CodeInsightContext {
    log.trace { "infer context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    val preferredContext = getPreferredContext(fileViewProvider.virtualFile)

    val setContext = trySetContext(fileViewProvider, preferredContext)

    if (setContext != anyContext()) {
      // at the moment, we do not allow context assignment in versioned environment
      InternalPsiVersioning.assertNotInFreezePsiVersion()
    }

    log.trace { "context of FileViewProvider ${fileViewProvider.virtualFile.path} is set to $setContext" }

    return setContext
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
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

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
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

    // First, so a test provider owns the files it claims, like a product provider declaring order="first".
    EP_NAME.point.registerExtension(provider, LoadingOrder.FIRST, disposable)

    // The EP change listener invalidates asynchronously, so invalidate here too: a test reading contexts right after
    // registering would otherwise see the pre-registration cache.
    runWriteAction {
      invalidateAllContexts()
    }
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
 * a single [CodeInsightContext] or an array of [CodeInsightContext]s
 */
private typealias ContextOrArray = Any

private fun ContextOrArray.getFirstContextOrNull(): CodeInsightContext? {
  @Suppress("UNCHECKED_CAST")
  return when (this) {
    is Array<*> -> (this as Array<CodeInsightContext>).firstOrNull()
    else -> this as CodeInsightContext
  }
}

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

private fun ensureReadAccess(file: VirtualFile) {
  if (file !is LightVirtualFile) {
    // FIXME: the assert had never worked due to IJPL-221633, but when it is enabled some tests fail
    // ThreadingAssertions.softAssertBackgroundThread()
    ThreadingAssertions.softAssertReadAccess()
  }
}
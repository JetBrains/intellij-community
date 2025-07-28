// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManagerEx
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.AtomicMapCache
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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
    fun getInstanceImpl(project: Project): CodeInsightContextManagerImpl = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
  }

  private val allContexts: AtomicMapCache<VirtualFile, List<CodeInsightContext>, ConcurrentMap<VirtualFile, List<CodeInsightContext>>> = AtomicMapCache { CollectionFactory.createConcurrentWeakMap() }
  private val preferredContext: AtomicMapCache<VirtualFile, CodeInsightContext, ConcurrentMap<VirtualFile, CodeInsightContext>> = AtomicMapCache { CollectionFactory.createConcurrentWeakMap() }

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
  }

  private fun subscribeToChanges() {
    invalidationProcessorJob?.cancel()
    invalidationProcessorJob = cs.launch {
      EP_NAME.extensionList.map { it.invalidationRequestFlow(project) }.merge().collectLatest {
        invalidateAllContexts()
      }
    }
  }

  override fun <Result> performCodeInsightSession(context: CodeInsightContext, block: CodeInsightSession.() -> Result): Result {
    val session = CodeInsightSessionImpl(context)
    return installThreadContext(currentThreadContext() + CodeInsightSessionElement(session)) {
      block(session)
    }
  }

  override val currentCodeInsightSession: CodeInsightSession?
    get() = com.intellij.codeInsight.multiverse.currentCodeInsightSession

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun getCodeInsightContexts(file: VirtualFile): List<CodeInsightContext> {
    //ThreadingAssertions.softAssertBackgroundThread()
    //ThreadingAssertions.softAssertReadAccess()

    if (!isSharedSourceSupportEnabled(project)) return listOf(defaultContext())

    return allContexts.getOrPut(file) {
      log.trace { "requested all contexts of file ${file.path}" }
      getContextSequence(file).toList()
    }
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

    //ThreadingAssertions.softAssertBackgroundThread()
    //ThreadingAssertions.softAssertReadAccess()

    log.trace { "requested preferred context of file ${file.path}" }

    return preferredContext.getOrPut(file) {
      findFirstContext(file)
    }
  }

  override fun getCodeInsightContext(fileViewProvider: FileViewProvider): CodeInsightContext {
    if (!isSharedSourceSupportEnabled(project)) return defaultContext()

    log.trace { "requested context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    val context = getCodeInsightContextRaw(fileViewProvider)

    if (context == anyContext()) {
      return inferContext(fileViewProvider)
    }

    return context
  }

  override fun getOrSetContext(fileViewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext {
    log.trace { "requested getOrSet context of FileViewProvider ${fileViewProvider.virtualFile.path}" }

    val rawContext = getCodeInsightContextRaw(fileViewProvider)
    if (rawContext != anyContext()) {
      return rawContext
    }

    if (context !in getContextSequence(fileViewProvider.virtualFile)) {
      return inferContext(fileViewProvider)
    }

    return trySetContext(fileViewProvider, context)
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
    log.assertTrue(preferredContext != anyContext()) { "preferredContext must not be anyContext" }

    val setContext = trySetContext(fileViewProvider, preferredContext)

    log.trace { "context of FileViewProvider ${fileViewProvider.virtualFile.path} is set to $setContext" }

    return setContext
  }

  private fun trySetContext(
    fileViewProvider: FileViewProvider,
    preferredContext: CodeInsightContext,
  ): CodeInsightContext {
    val fileManager = PsiManagerEx.getInstanceEx(project).fileManager as? FileManagerEx
    if (fileManager != null) {
      val result = fileManager.trySetContext(fileViewProvider, preferredContext)
      if (result != null) {
        return result
      }

      // let's make sure fileViewProvider is still valid
      val mainPsi = fileViewProvider.getPsi(fileViewProvider.baseLanguage)
      if (mainPsi != null) {
        PsiUtilCore.ensureValid(mainPsi)
      }
    }

    setCodeInsightContext(fileViewProvider, preferredContext)
    return preferredContext
  }

  /**
   * does not infer the substitution for `anyContext`
   */
  fun getCodeInsightContextRaw(fileViewProvider: FileViewProvider): CodeInsightContext =
    fileViewProvider.getUserData(codeInsightContextKey) ?: defaultContext()

  fun setCodeInsightContext(fileViewProvider: FileViewProvider, context: CodeInsightContext) {
    log.trace { "set context of FileViewProvider ${fileViewProvider.virtualFile.path} to $context" }

    val effectiveContext = context.takeUnless { it == defaultContext() }
    fileViewProvider.putUserData(codeInsightContextKey, effectiveContext)
  }

  override val isSharedSourceSupportEnabled: Boolean
    get() {
      project.getUserData(multiverse_enabler_key)?.let { return it }

      synchronized(this) {
        project.getUserData(multiverse_enabler_key)?.let { return it }
        val result = computeSharedSourceEnabled()
        project.putUserData(multiverse_enabler_key, result)
        log.info("multiverse is ${if (result) "enabled" else "disabled"}")
        return result
      }
    }

  private fun computeSharedSourceEnabled(): Boolean {
    @Suppress("TestOnlyProblems")
    if (ApplicationManager.getApplication().isUnitTestMode && MultiverseTestEnabler.getValueAndErase()) {
      return true
    }

    val result = MULTIVERSE_ENABLER_EP_NAME.extensionList.any { enabler ->
      runSafely { enabler.enableMultiverse(project) } == true
    }
    return result
  }
}

private class CodeInsightSessionImpl(
  override val context: CodeInsightContext
) : CodeInsightSession

private val EP_NAME = ExtensionPointName.create<CodeInsightContextProvider>("com.intellij.multiverse.codeInsightContextProvider")

private val codeInsightContextKey = Key.create<CodeInsightContext>("codeInsightContextKey")

private val log = logger<CodeInsightContextManagerImpl>()

private val multiverse_enabler_key = Key.create<Boolean>("shared.source.support.enabled")

private val MULTIVERSE_ENABLER_EP_NAME : ExtensionPointName<MultiverseEnabler> = ExtensionPointName.create("com.intellij.multiverseEnabler")

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

@TestOnly
@ApiStatus.Internal
object MultiverseTestEnabler {
  private val value = AtomicBoolean()

  fun enableSharedSourcesForTheNextProject() {
    val prev = value.getAndSet(true)
    if (prev) {
      throw IllegalStateException("multiverse is already enabled")
    }
  }

  internal fun getValueAndErase(): Boolean {
    val prev = value.getAndSet(false)
    return prev
  }
}

internal sealed interface SetContextResult {
  /** the context was successfully installed */
  object Success : SetContextResult

  /** the context was not installed because of a concurrent context update */
  class ConcurrentlyUpdated(val newContext: CodeInsightContext) : SetContextResult

  /** the context was not installed because the file view provider is missing in the file manager storage */
  object ProviderIsMissing : SetContextResult
}
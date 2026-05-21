// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import com.intellij.concurrency.ExternalIntelliJContextElement
import com.intellij.concurrency.IntelliJThreadContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.WriteLockReacquisitionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.coroutines.CoroutineContext

// object is used for namespace qualification
@Internal
object InternalPsiVersioning {

  private val PERSISTENT_PSI_ENABLED: Boolean by lazy { Registry.`is`("psi.enable.persistent.syntax.tree", false) }
  private const val NESTED_LOCKS_THREADING_SUPPORT_CLASS_NAME = "com.intellij.platform.locking.impl.NestedLocksThreadingSupport"
  private const val SUSPENDING_WRITE_ACTION_METHOD_NAME = "executeSuspendingWriteAction"

  private const val LOCK_PROHIBITION_FREEZE_PSI_VERSION_ADVICE = "Lock usage is forbidden by `PsiVersioningService#freezePsiVersion`. It is not allowed to use locks while PSI snapshot is frozen"

  // a reading operation with the available psi version
  fun <T> freezePsiVersion(action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      return action()
    }
    val registry = PsiVersionRegistry.instance
    val latestVersion = registry.latestPublishedVersion
    return ApplicationManagerEx.getApplicationEx().withLocksProhibited(LOCK_PROHIBITION_FREEZE_PSI_VERSION_ADVICE) {
      initFreezePsiVersionSection(false, latestVersion).use {
        action()
      }
    }
  }

  @JvmStatic
  fun assertNotInFreezePsiVersion() {
    if (PsiVersionFreezeMarker.isAvailable()) {
      error("This function is not allowed inside `freezePsiVersion` block")
    }
  }

  @JvmStatic
  fun getCurrentPsiVersionInsideFrozenPsi(): Long? {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      return null
    }
    val writeVersion = currentThreadContext()[PsiVersionWriteContextElement.Key]
    if (writeVersion != null) {
      // the case where we are in currently running write action
      return writeVersion.version
    }
    return currentThreadContext()[PsiVersionFreezeMarker.Key]?.version
  }

  /**
   * Helper function to check invariants of versioned PSI. Should not be used for business logic.
   */
  @JvmStatic
  fun isInsideVersioningButNotLocks(): Boolean {
    return getCurrentPsiVersionInsideFrozenPsi() != null
  }

  /**
   * An analogue of [ThreadingAssertions.assertReadAccess] but permits running in a versioned environment.
   *
   * ```kotlin
   * runReadAction {
   *   assertReadAccessOrVersionedEnvironment() // does not throw
   * }
   * freezePsiVersion {
   *   assertReadAccessOrVersionedEnvironment() // does not throw
   * }
   * ```
   */
  @JvmStatic
  fun assertReadAccessOrVersionedEnvironment() {
    if (isInsideVersioningButNotLocks()) {
      return
    }
    ThreadingAssertions.assertReadAccess()
  }

  @JvmStatic
  fun getCurrentPsiVersion(): Long {
    val tlValue = threadLocalVersioningTracker.get()
    if (tlValue != null) {
      return tlValue
    }
    // Unfortunately, throughout our codebase we have interactions with PSI that are not protected by any lock, especially in tests
    // Technically, it is possible to wrap all such cases in a read action/freezePsiVersion, but we decided to avoid useless assertions for now
    // also, this is a very hot path, so we'd like to avoid retrieval of service here
    return PsiVersionRegistry.instance.latestPublishedVersion
  }

  /**
   * A version that is used to mark an element as versioned or non-versioned
   */
  @JvmStatic
  fun getCreationPsiVersionForElement(): Long {
    return if (PERSISTENT_PSI_ENABLED && isVersionedComputation()) {
      getCurrentPsiVersion()
    } else {
      -1
    }
  }

  /**
   * We assert that it is allowed to modify versioned syntax trees only
   * in [com.intellij.openapi.application.Application.runWriteAction] or [runModificationOfVersionedPsi]
   */
  @JvmStatic
  fun assertWritePsiModificationAllowed() {
    val versionFromThreadLocal = threadLocalVersioningTracker.get()
    val writeVersionFromContext = currentThreadContext()[PsiVersionWriteContextElement.Key]
    if (writeVersionFromContext == null) {
      throw IllegalStateException("Versioned PSI modification is allowed only in write actions or `InternalPsiVersioning.runWriteModification`")
    }
    if (writeVersionFromContext.version != versionFromThreadLocal) {
      throw IllegalStateException("Version from write context element (${writeVersionFromContext.version}) must be the same as version from thread local ($versionFromThreadLocal)")
    }
  }

  private class PsiVersionFreezeMarker(val version: Long): IntelliJThreadContextElement<Boolean> {
    companion object {
      val threadLocalStorage: ThreadLocal<Unit?> = ThreadLocal.withInitial { null }
      fun isAvailable(): Boolean = threadLocalStorage.get() != null
    }

    object Key : CoroutineContext.Key<PsiVersionFreezeMarker>
    override val key: CoroutineContext.Key<*> = Key

    override fun beforeStarted(context: CoroutineContext): Boolean {
      if (threadLocalStorage.get() == null) {
        threadLocalStorage.set(Unit)
        return true
      }
      return false
    }

    override fun afterCompleted(context: CoroutineContext, wasInstalled: Boolean) {
      if (wasInstalled) {
        threadLocalStorage.remove()
      }
    }
  }

  typealias PsiVersion = Long

  private class PsiVersionWriteContextElement(val version: Long): IntelliJThreadContextElement<PsiVersion?>, ExternalIntelliJContextElement, ThreadContextElement<PsiVersion?> {
    object Key : CoroutineContext.Key<PsiVersionWriteContextElement>
    override val key: CoroutineContext.Key<*> = Key

    override fun beforeStarted(context: CoroutineContext): PsiVersion? {
      val currentValue = threadLocalVersioningTracker.get()
      threadLocalVersioningTracker.set(version)
      return currentValue
    }

    override fun updateThreadContext(context: CoroutineContext): PsiVersion? {
      return beforeStarted(context)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: PsiVersion?) {
      afterCompleted(context, oldState)
    }

    override fun afterCompleted(context: CoroutineContext, oldState: PsiVersion?) {
      val currentValue = threadLocalVersioningTracker.get()
      if (currentValue == null) {
        return // cancellation
      }
      threadLocalVersioningTracker.set(oldState)
    }

    override fun toString(): String {
      return "<PsiWriteVersion:$version>"
    }
  }



  // Conceptually, the PSI version is propagated via thread contexts.
  // However, thread contexts are computationally expensive, so we rely rather on a thread-local here.
  // But even thread-local leaves a considerable footprint, so we need to avoid too frequent access to it.
  private val threadLocalVersioningTracker: ThreadLocal<PsiVersion?> = ThreadLocal.withInitial { null }

  // write actions are application-side events, hence versioning works across the whole app
  @Internal
  class PsiVersionRegistry {
    companion object {
      @JvmStatic
      val instance: PsiVersionRegistry by lazy { PsiVersionRegistry() }
    }

    private val version = AtomicLong(0)

    val latestPublishedVersion: Long
      get() = version.get()

    fun incrementVersion(expected: Long) {
      val versionAdvanced = version.compareAndSet(expected, expected + 1)
      assert(versionAdvanced) {
        "Version modification failed: could not increment the version with $expected, because global version version is ${version.get()}"
      }
    }

    fun getFrozenKeys(): Set<Long> {
      return setOf(latestPublishedVersion)
    }
  }


  internal class PsiVersioningWriteActionActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      addListeners()
    }

    @Suppress("CompanionObjectInExtension")
    companion object {
      val listenerAdded = AtomicBoolean(false)

      @JvmStatic
      fun addListeners() {
        val listenersAllowed = Registry.`is`("psi.enable.persistent.syntax.tree.locking.listener") || isVersionedSyntaxTreeEnabled()
        if (!listenersAllowed || listenerAdded.getAndSet(true)) {
          return
        }
        val writeActionListener = PsiVersioningLockingListener()
        ApplicationManagerEx.getApplicationEx().addWriteActionListener(writeActionListener, ApplicationManager.getApplication())
        ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(writeActionListener, ApplicationManager.getApplication())
        ApplicationManagerEx.getApplicationEx().addReadActionListener(writeActionListener, ApplicationManager.getApplication())
        ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(writeActionListener, ApplicationManager.getApplication())
      }
    }

  }

  // because of transfers of write action, we cannot use therad-local
  private val cleanupTokenList: ThreadLocal<MutableList<AccessToken>> = ThreadLocal.withInitial { mutableListOf() }

  // Allows mutating some data while a PSI version is frozen.
  // For example, it can be used for lazy parsing.
  // A dangerous function, use it with care!
  @JvmStatic
  fun <T> runModificationOfVersionedPsi(action: Supplier<T>): T {
    val currentVersion = currentThreadContext()[PsiVersionWriteContextElement.Key]
    if (currentVersion != null) {
      return action.get()
    }
    val currentReadStamp = getCurrentPsiVersion()
    return initFreezePsiVersionSection(true, currentReadStamp).use {
      action.get()
    }
  }

  sealed interface VersioningKey {
    object Collapsed : VersioningKey
    object Versioned: VersioningKey
  }

  // thread-local state of computation -- i.e., whether it is versioned or not
  private val versionedComputationKey: ThreadLocal<VersioningKey> = ThreadLocal.withInitial { VersioningKey.Collapsed }

  /**
   * Indicates whether the current computation is running in a versioned environment.
   * In most cases, it is indetical to `Application#isWriteAccessAllowed`
   */
  @JvmStatic
  fun isVersionedComputation(): Boolean {
    return versionedComputationKey.get() === VersioningKey.Versioned
  }

  /**
   * Low-level way of running a PSI operation in the versioned environment.
   * Consider using [com.intellij.psi.util.PsiVersioningService] for public code
   */
  @JvmStatic
  fun <T> inVersionedEnvironment(isVersioned: Boolean, action: Supplier<T>): T {
    val oldValue = versionedComputationKey.get()
    val toInstall = if (isVersioned) VersioningKey.Versioned else VersioningKey.Collapsed
    if (oldValue == toInstall) {
      return action.get()
    } else {
      versionedComputationKey.set(toInstall)
      try {
        return action.get()
      } finally {
        versionedComputationKey.set(oldValue)
      }
    }
  }

  @VisibleForTesting
  @Internal
  class PsiVersioningLockingListener : WriteActionListener, WriteLockReacquisitionListener<Unit>, ReadActionListener, WriteIntentReadActionListener {
    override fun writeActionStarted(action: Class<*>) {
      val token = initWriteActionSection()
      cleanupTokenList.get().add(token)
    }

    override fun writeActionFinished(action: Class<*>) {
      cleanupVersioningSection()
    }

    override fun beforeWriteLockTemporarilyReleased(): Unit {
      writeActionFinished(Any::class.java) // we publish the incremented version here so that the published version is incremented
      val token = initReadActionSection()
      cleanupTokenList.get().add(token)
      return
    }

    override fun beforeWriteLockReacquired(data: Unit) {
    }

    override fun afterWriteLockReacquired(data: Unit) {
      cleanupVersioningSection()
      writeActionStarted(Any::class.java)
    }

    override fun readActionStarted(action: Class<*>) {
      val token = initReadActionSection()
      cleanupTokenList.get().add(token)
    }

    override fun readActionFinished(action: Class<*>) {
      cleanupVersioningSection()
    }

    override fun beforeWriteLockParallelizationEnds(isWriteActionPending: Boolean) {
      val currentData = threadLocalVersioningTracker.get()
      val actualVersion = PsiVersionRegistry.instance.latestPublishedVersion
      if (currentData != null) {
        threadLocalVersioningTracker.set(actualVersion)
      }
    }
  }

  fun initFreezePsiVersionSection(write: Boolean, latestVersion: Long): AccessToken {
    val elementMarker = if (write) PsiVersionWriteContextElement(latestVersion) else PsiVersionFreezeMarker(latestVersion)
    @Suppress("DEPRECATION") val threadContextToken = installThreadContext(currentThreadContext() + elementMarker, true)
    val installedVersion = threadLocalVersioningTracker.get()
    val combinedToken = if (installedVersion == null) {
      threadLocalVersioningTracker.set(latestVersion)
      object : AccessToken() {
        override fun finish() {
          threadLocalVersioningTracker.remove()
          threadContextToken.finish()
        }
      }
    } else {
      require(installedVersion == latestVersion) {
        "Thread-local version mismatch for reentrancy token. Expected: $latestVersion, actual: ${installedVersion}"
      }
      threadContextToken
    }
    return combinedToken
  }

  fun initWriteActionSection(): AccessToken {
    val storedThreadLocal = threadLocalVersioningTracker.get()
    return if (storedThreadLocal == null
               // this branch can happen in tests where the context is reset to dispatch some events synchronously
               || currentThreadContext()[PsiVersionWriteContextElement.Key] == null) {
      val psiVersionRegistry = PsiVersionRegistry.instance
      val existingVersion = psiVersionRegistry.latestPublishedVersion
      val newVersion = existingVersion + 1
      @Suppress("DEPRECATION")
      val threadContextInstallation = installThreadContext(currentThreadContext() + PsiVersionWriteContextElement(newVersion), true)
      threadLocalVersioningTracker.set(newVersion)
      object : AccessToken() {
        override fun finish() {
          threadContextInstallation.finish()
          threadLocalVersioningTracker.remove()
          val latestVersion = PsiVersionRegistry.instance.latestPublishedVersion
          PsiVersionRegistry.instance.incrementVersion(latestVersion)
        }
      }
    } else {
      AccessToken.EMPTY_ACCESS_TOKEN
    }
  }

  fun initReadActionSection(): AccessToken {
    val latestVersion = PsiVersionRegistry.instance.latestPublishedVersion
    val writeElementVersion = currentThreadContext()[PsiVersionWriteContextElement.Key]?.version
    val correctVersion = when {
      writeElementVersion != null -> writeElementVersion
      // this condition can happen if we are in service initialization, where contexts are independent from the thread
      ApplicationManager.getApplication().isWriteAccessAllowed -> latestVersion + 1
      else -> latestVersion
    }
    val value = threadLocalVersioningTracker.get()
    return if (value == null) {
      threadLocalVersioningTracker.set(correctVersion)
      object : AccessToken() {
        override fun finish() {
          threadLocalVersioningTracker.remove()
        }
      }
    } else {
      // we hope that eventually the problem with suspending write actions will be resolved; but we suppress the error for a known offender for now
      // let's report these errors only for internal builds -- persistent syntax has no effect anyway
      if (correctVersion != value && ApplicationManager.getApplication().isInternal && !isInSuspendingWriteAction()) {
        try {
          // known case: this breaks is someone executed "suspending write action"
          thisLogger().error("Expected version $correctVersion, but found $value; write access: ${ApplicationManager.getApplication().isWriteAccessAllowed}; context: ${currentThreadContext()}")
        } catch (e : AssertionError) {
          // in tests, the error above throws a hard error which corrupts the stack of cleanups.
          // we hope that the error will be reported and the rest of the program proceeds as expected
          if (!ApplicationManager.getApplication().isUnitTestMode) {
            throw e
          }
        }
      }
      AccessToken.EMPTY_ACCESS_TOKEN
    }
  }

  private fun isInSuspendingWriteAction(): Boolean {
    return Throwable().stackTrace.any { stackTraceElement ->
      stackTraceElement.className == NESTED_LOCKS_THREADING_SUPPORT_CLASS_NAME &&
      stackTraceElement.methodName == SUSPENDING_WRITE_ACTION_METHOD_NAME
    }
  }

  override fun toString(): String {
    val explanation = if (getCurrentPsiVersion() % 2 == 1L) {
      " (Odd version -- changes are running in exclusive modification scope and they are invisible to anyone but this thread)"
    } else if (getCurrentPsiVersion() % 2 == 0L && getCurrentPsiVersion() > PsiVersionRegistry.instance.latestPublishedVersion) {
      " (Thread-local version is ahead of the published version -- the changes happening in a write action and they will be published)"
    } else {
      ""
    }
    return "Psi Versioning Ecosystem State: latestVersion=${PsiVersionRegistry.instance.latestPublishedVersion}, in frozen PSI=${isInsideVersioningButNotLocks()}, version for this thread=${getCurrentPsiVersion()}${explanation}"
  }

  fun cleanupVersioningSection() {
    cleanupTokenList.get().removeAt(cleanupTokenList.get().lastIndex).close()
  }

  @JvmStatic
  fun isVersionedSyntaxTreeEnabled(): Boolean {
    return PERSISTENT_PSI_ENABLED
  }
}
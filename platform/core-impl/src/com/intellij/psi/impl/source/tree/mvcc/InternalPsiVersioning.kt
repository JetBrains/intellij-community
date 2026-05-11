// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

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
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.coroutines.CoroutineContext
import kotlin.getValue

// object is used for namespace qualification
@Internal
object InternalPsiVersioning {
  // a reading operation with the available psi version
  fun <T> freezePsiVersion(action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      return action()
    }
    val registry = PsiVersionRegistry.instance
    val latestVersion = registry.latestPublishedVersion
    return initFreezePsiVersionSection(false, latestVersion).use {
      action()
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
    return if (Registry.`is`("psi.enable.persistent.syntax.tree", false) && isVersionedComputation()) {
      getCurrentPsiVersion()
    } else {
      -1
    }
  }

  /**
   * A version that is used in mutating operations for updating syntax elements.
   * It is made available, for example, in [com.intellij.openapi.application.Application.runWriteAction] or [runWriteModification]
   */
  @JvmStatic
  fun getCurrentPsiVersionForWrite(): Long {
    val versionFromThreadLocal = threadLocalVersioningTracker.get()
    val versionFromContextElement = currentThreadContext()[PsiVersionWriteContextElement.Key]?.version
    require(versionFromContextElement == null || versionFromContextElement == versionFromThreadLocal) {
      "Version from thread-local must be the same as version from thread context"
    }
    val versionElement = requireNotNull(versionFromThreadLocal) {
      "Attempt to modify persistent PSI without explicit write transaction. Solutions:\n" +
      "1. Use explicit `runWriteAction`;\n" +
      "2. The mutated element must not belong to persistent tree;\n" +
      "3. If other solutions are impossible, use `runWriteModification`"
    }
    return versionElement
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

  private class PsiVersionWriteContextElement(val version: Long): IntelliJThreadContextElement<PsiVersion?>, ThreadContextElement<PsiVersion?> {
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
      assert(version.compareAndSet(expected, expected + 1)) {
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
        if (listenerAdded.getAndSet(true)) {
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
  fun <T> runWriteModification(action: Supplier<T>): T {
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
  class PsiVersioningLockingListener : WriteActionListener, WriteLockReacquisitionListener<MutableList<AccessToken>>, ReadActionListener, WriteIntentReadActionListener {
    override fun writeActionStarted(action: Class<*>) {
      val token = initWriteActionSection()
      cleanupTokenList.get().add(token)
    }

    override fun writeActionFinished(action: Class<*>) {
      cleanupVersioningSection()
    }

    override fun beforeWriteLockTemporarilyReleased(): MutableList<AccessToken> {
      val publishedVersion = PsiVersionRegistry.instance.latestPublishedVersion
      PsiVersionRegistry.instance.incrementVersion(publishedVersion)
      val currentCleanupTokenListData = cleanupTokenList.get()
      cleanupTokenList.remove()
      // clear thread local reentrancy tracker, run writeActionStarted again
      writeActionStarted(Any::class.java)
      return currentCleanupTokenListData
    }

    override fun beforeWriteLockReacquired(list: MutableList<AccessToken>) {
      writeActionFinished(Any::class.java)
      cleanupTokenList.set(list)
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
    return if (storedThreadLocal == null) {
      val psiVersionRegistry = PsiVersionRegistry.instance
      val existingVersion = psiVersionRegistry.latestPublishedVersion
      val newVersion = existingVersion + 1
      @Suppress("DEPRECATION")
      val threadContextInstallation = installThreadContext(currentThreadContext() + PsiVersionWriteContextElement(newVersion), true)
      threadLocalVersioningTracker.set(newVersion)
      // Intention actions and quick-fixes often create synthetic PSI elements and insert them to the main live tree.
      // To support such use-case, we make all synthetic elements automatically versioned. This helps to avoid assertions about the mismatch of direct and versioned nodes.
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
    val correctVersion = if (ApplicationManager.getApplication().isWriteAccessAllowed) latestVersion + 1 else latestVersion
    val value = threadLocalVersioningTracker.get()
    return if (value == null) {
      threadLocalVersioningTracker.set(correctVersion)
      object : AccessToken() {
        override fun finish() {
          threadLocalVersioningTracker.remove()
        }
      }
    } else {
      // there can be a mismatch between a global version and the installed version -- if, for example, someone calls `runReadActionBlocking` inside `freezePsiVersion`
      AccessToken.EMPTY_ACCESS_TOKEN
    }
  }

  fun cleanupVersioningSection() {
    cleanupTokenList.get().removeAt(cleanupTokenList.get().lastIndex).close()
  }
}
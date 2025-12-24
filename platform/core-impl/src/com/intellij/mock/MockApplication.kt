// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION")

package com.intellij.mock

import com.intellij.lang.MetaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.AnyModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.concurrent.Volatile

@Internal
open class MockApplication(parentDisposable: Disposable) : MockComponentManager(null, parentDisposable), ApplicationEx {
  companion object {
    var INSTANCES_CREATED: Int = 0
      private set

    @TestOnly
    @JvmStatic
    fun setUp(parentDisposable: Disposable): MockApplication {
      val app = MockApplication(parentDisposable)
      ApplicationManager.setApplication(app, parentDisposable)
      return app
    }

    private val LOG: Logger
      get() = logger<MockApplication>()

    @Volatile
    private var warningLogged = false
  }

  @Suppress("RAW_SCOPE_CREATION")
  private val appCoroutineScope = CoroutineScope(SupervisorJob())

  init {
    INSTANCES_CREATED++
    @Suppress("TestOnlyProblems")
    Extensions.setRootArea(getExtensionArea(), parentDisposable)
  }

  override fun <T> getServiceIfCreated(serviceClass: Class<T>): T? = doGetService(serviceClass = serviceClass, createIfNeeded = false)

  override fun <T> getService(serviceClass: Class<T>): T? = doGetService(serviceClass = serviceClass, createIfNeeded = true)

  private fun <T> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    super.getService(serviceClass)?.let {
      return it
    }
    if (createIfNeeded && Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)) {
      synchronized(serviceClass) {
        super.getService(serviceClass)?.let {
          return it
        }

        picoContainer.registerComponentImplementation(serviceClass.getName(), serviceClass)
        return super.getService<T?>(serviceClass)
      }
    }
    return null
  }

  override fun isInternal(): Boolean = false

  override fun isEAP(): Boolean = false

  override fun getCoroutineScope(): CoroutineScope = appCoroutineScope

  override fun isDispatchThread(): Boolean = EDT.isCurrentThreadEdt()

  override fun isWriteIntentLockAcquired(): Boolean = true

  override fun isActive(): Boolean = true

  override fun assertReadAccessAllowed() {
  }

  override fun assertWriteAccessAllowed() {
  }

  override fun assertReadAccessNotAllowed() {
  }

  override fun assertIsDispatchThread() {
  }

  override fun assertIsNonDispatchThread() {
  }

  override fun assertWriteIntentLockAcquired() {
  }

  override fun isReadAccessAllowed(): Boolean = true

  override fun isWriteAccessAllowed(): Boolean = true

  override fun isUnitTestMode(): Boolean = true

  override fun isHeadlessEnvironment(): Boolean = true

  override fun isCommandLine(): Boolean = true

  override fun executeOnPooledThread(action: Runnable): Future<*> = AppExecutorUtil.getAppExecutorService().submit(action)

  override fun <T> executeOnPooledThread(action: Callable<T?>): Future<T?> = AppExecutorUtil.getAppExecutorService().submit<T?>(action)

  override fun isRestartCapable(): Boolean = false

  override fun invokeLaterOnWriteThread(action: Runnable) {
    action.run()
  }

  override fun invokeLaterOnWriteThread(action: Runnable, modal: ModalityState) {
    action.run()
  }

  override fun invokeLaterOnWriteThread(action: Runnable, modal: ModalityState, expired: Condition<*>) {
    action.run()
  }

  override fun runReadAction(action: Runnable) {
    action.run()
  }

  override fun <T> runReadAction(computation: Computable<T?>): T? {
    return computation.compute()
  }

  override fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T?, E?>): T? {
    return computation.compute()
  }

  override fun runWriteAction(action: Runnable) {
    action.run()
  }

  override fun <T> runWriteAction(computation: Computable<T?>): T? {
    return computation.compute()
  }

  override fun <T, E : Throwable?> runWriteAction(computation: ThrowableComputable<T?, E?>): T? {
    return computation.compute()
  }

  override fun hasWriteAction(actionClass: Class<*>): Boolean = false

  override fun addApplicationListener(listener: ApplicationListener) {
  }

  override fun addApplicationListener(listener: ApplicationListener, parent: Disposable) {
  }

  override fun removeApplicationListener(listener: ApplicationListener) {
  }

  override fun getStartTime(): Long = 0

  override fun getIdleTime(): Long = 0

  override fun getNoneModalityState(): ModalityState = ModalityState.nonModal()

  override fun invokeLater(runnable: Runnable, expired: Condition<*>) {
    logInsufficientIsolation("invokeLater", runnable, expired)
    SwingUtilities.invokeLater(runnable)
  }

  override fun invokeLater(runnable: Runnable, state: ModalityState, expired: Condition<*>) {
    logInsufficientIsolation("invokeLater", runnable, state, expired)
    SwingUtilities.invokeLater(runnable)
  }

  override fun invokeLater(runnable: Runnable) {
    logInsufficientIsolation("invokeLater", runnable)
    SwingUtilities.invokeLater(runnable)
  }

  override fun invokeLater(runnable: Runnable, state: ModalityState) {
    logInsufficientIsolation("invokeLater", runnable, state)
    SwingUtilities.invokeLater(runnable)
  }

  override fun invokeAndWait(runnable: Runnable, modalityState: ModalityState) {
    if (isDispatchThread()) {
      runnable.run()
    }
    else {
      try {
        SwingUtilities.invokeAndWait(runnable)
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }

  @Throws(ProcessCanceledException::class)
  override fun invokeAndWait(runnable: Runnable) {
    invokeAndWait(runnable, defaultModalityState)
  }

  override fun getCurrentModalityState(): ModalityState = getNoneModalityState()

  override fun getAnyModalityState(): ModalityState = AnyModalityState.ANY

  override fun getModalityStateForComponent(c: Component): ModalityState = getNoneModalityState()

  override fun getDefaultModalityState(): ModalityState = getNoneModalityState()

  override fun saveAll() {
  }

  override fun saveSettings() {
  }

  override fun holdsReadLock(): Boolean {
    return false
  }

  override fun restart(exitConfirmed: Boolean) {
  }

  override fun runProcessWithProgressSynchronously(
    process: Runnable,
    progressTitle: String,
    canBeCanceled: Boolean,
    modal: Boolean,
    project: Project?,
    parentComponent: JComponent?,
    cancelText: String?
  ): Boolean {
    return false
  }

  override fun assertIsDispatchThread(component: JComponent?) {
  }

  override fun tryRunReadAction(runnable: Runnable): Boolean {
    runReadAction(runnable)
    return true
  }

  override fun isWriteActionInProgress(): Boolean {
    return false
  }

  override fun isWriteActionPending(): Boolean {
    return false
  }

  override fun isSaveAllowed(): Boolean {
    return true
  }

  override fun setSaveAllowed(value: Boolean) {
  }

  override fun dispose() {
    try {
      // A mock application may cause incorrect caching during tests. It does not fire extension point removed events.
      // Ensure that we have cached against correct application.
      MetaLanguage.clearAllMatchingMetaLanguagesCache()
      appCoroutineScope.cancel()
    }
    finally {
      super.dispose()
    }
  }

  private fun logInsufficientIsolation(@Suppress("SameParameterValue") methodName: String?, vararg args: Any?) {
    if (warningLogged || !isUnitTestMode()) {
      return
    }

    warningLogged = true
    LOG.warn(
      "Attempt to execute method \"" + methodName + "\" with arguments `" + args.contentToString() + "` within a MockApplication.\n" +
      "This is likely caused by an improper test isolation. Please consider writing tests with JUnit 5 fixtures.",
      Throwable())
  }
}

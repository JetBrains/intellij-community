// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.cache

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.FileCodeStyleProvider
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ArrayUtil
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

private val LOG = logger<CodeStyleCachedValueProvider>()

@Service(Service.Level.PROJECT)
private class CodeStyleCachedValueProviderService(@JvmField val coroutineScope: CoroutineScope) {
}

internal class CodeStyleCachedValueProvider(private val viewProvider: FileViewProvider,
                                            private val project: Project) : CachedValueProvider<CodeStyleSettings?> {
  private val computation = AsyncComputation(project)
  private val computationLock: Lock = object : ReentrantLock() {
    override fun equals(other: Any?): Boolean = other is ReentrantLock
  }

  val isExpired: Boolean
    get() = computation.isExpired

  fun tryGetSettings(): CodeStyleSettings? {
    if (computationLock.tryLock()) {
      try {
        return CachedValuesManager.getManager(project).getCachedValue(viewProvider, this)
      }
      finally {
        computationLock.unlock()
      }
    }
    else {
      return null
    }
  }

  fun scheduleWhenComputed(runnable: Runnable) {
    computation.schedule(runnable)
  }

  override fun compute(): CachedValueProvider.Result<CodeStyleSettings?>? {
    val settings: CodeStyleSettings?
    try {
      settings = computation.getCurrentResult()
    }
    catch (ignored: ProcessCanceledException) {
      computation.reset()
      LOG.debug { "Computation was cancelled for ${viewProvider.virtualFile.name}" }
      return CachedValueProvider.Result(null, ModificationTracker.EVER_CHANGED)
    }
    if (settings != null) {
      logCached(settings)
      return CachedValueProvider.Result(settings, *getDependencies(settings, computation))
    }
    return null
  }

  fun cancelComputation() {
    computation.cancel()
  }

  private fun getDependencies(settings: CodeStyleSettings, computation: AsyncComputation): Array<Any> {
    val dependencies = ArrayList<Any?>()
    if (settings is TransientCodeStyleSettings) {
      dependencies.addAll(settings.dependencies)
    }
    else {
      dependencies.add(settings.modificationTracker)
    }
    dependencies.add(computation.tracker)
    return ArrayUtil.toObjectArray(dependencies)
  }

  private fun logCached(settings: CodeStyleSettings) {
    LOG.debug(String.format(
      "File: %s (%s), cached: %s, tracker: %d", viewProvider.virtualFile.name, Integer.toHexString(viewProvider.hashCode()),
      settings, settings.modificationTracker.modificationCount))
  }

  /**
   * Always contains some result which can be obtained by `getCurrResult()` method. Listeners are notified after
   * the computation is finished and `getCurrResult()` contains a stable computed value.
   */
  private inner class AsyncComputation(private val project: Project) {
    private val isActive = AtomicBoolean()

    @Volatile
    private var currentResult: CodeStyleSettings?
    private val settingsManager = CodeStyleSettingsManager.getInstance(project)

    @JvmField
    val tracker = SimpleModificationTracker()
    private var job: Job? = null
    private val scheduledRunnables = ArrayList<Runnable>()
    private var oldTrackerSetting: Long = 0
    private var insideRestartedComputation = false

    init {
      currentResult = settingsManager.currentSettings
    }

    private fun start() {
      val app = ApplicationManager.getApplication()
      if (app.isDispatchThread && !app.isUnitTestMode && !app.isHeadlessEnvironment) {
        LOG.debug { "async for ${viewProvider.virtualFile.name}" }
        job = project.service<CodeStyleCachedValueProviderService>().coroutineScope.launch {
          readAction {
            computeSettings()
          }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            notifyCachedValueComputed()
          }
        }
      }
      else {
        LOG.debug { "sync for ${viewProvider.virtualFile.name}" }
        app.runReadAction(::computeSettings)
        if (app.isDispatchThread) {
          notifyCachedValueComputed()
        }
        else {
          project.service<CodeStyleCachedValueProviderService>().coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            notifyCachedValueComputed()
          }
        }
      }
    }

    fun cancel() {
      LOG.debug { "expire computation for ${viewProvider.virtualFile.name}" }
      job?.cancel()
      currentResult = null
    }

    val isExpired: Boolean
      get() = currentResult == null

    fun schedule(runnable: Runnable) {
      if (isActive.get()) {
        scheduledRunnables.add(ClientId.decorateRunnable(runnable))
      }
      else {
        runnable.run()
      }
    }

    private fun computeSettings() {
      val file = viewProvider.virtualFile
      val psiFile = psiFile
      if (psiFile == null) {
        cancel()
        return
      }

      computationLock.lock()
      try {
        LOG.debug { "Computation started for ${file.name}" }
        var currSettings = getCurrentSettings(psiFile)
        oldTrackerSetting = currSettings.modificationTracker.modificationCount
        @Suppress("TestOnlyProblems")
        if (currSettings !== settingsManager.temporarySettings) {
          val modifiableSettings = TransientCodeStyleSettings(viewProvider, currSettings)
          modifiableSettings.applyIndentOptionsFromProviders(project, file)
          LOG.debug { "Created TransientCodeStyleSettings for ${file.name}" }
          for (modifier in CodeStyleSettingsModifier.EP_NAME.extensionList) {
            LOG.debug { "Modifying ${file.name}: ${modifier.javaClass.name}" }
            if (modifier.modifySettings(modifiableSettings, psiFile)) {
              LOG.debug { "Modified ${file.name}: ${modifier.javaClass.name}" }
              modifiableSettings.setModifier(modifier)
              currSettings = modifiableSettings
              break
            }
          }
        }

        if (currentResult !== currSettings) {
          currentResult = currSettings
          tracker.incModificationCount()
        }
        LOG.debug { "Computation ended for ${file.name}" }
      }
      finally {
        computationLock.unlock()
      }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSettings(file: PsiFile): CodeStyleSettings {
      val result = FileCodeStyleProvider.EP_NAME.computeSafeIfAny { it.getSettings(file) }
      return result ?: settingsManager.currentSettings
    }

    fun getCurrentResult(): CodeStyleSettings? {
      if (isActive.compareAndSet(false, true)) {
        LOG.debug { "Computation initiated for ${viewProvider.virtualFile.name}" }
        start()
      }
      return currentResult
    }

    fun reset() {
      scheduledRunnables.clear()
      isActive.set(false)
      LOG.debug { "Computation reset for ${viewProvider.virtualFile.name}" }
    }

    private fun notifyCachedValueComputed() {
      @Suppress("DEPRECATION")
      val currSettings = settingsManager.currentSettings
      val newTrackerSetting = currSettings.modificationTracker.modificationCount
      if (oldTrackerSetting < newTrackerSetting && !insideRestartedComputation) {
        insideRestartedComputation = true
        try {
          LOG.debug { "restarted for ${viewProvider.virtualFile.name}" }
          start()
        }
        finally {
          insideRestartedComputation = false
        }
        return
      }
      LOG.debug { "running scheduled runnables for ${viewProvider.virtualFile.name}" }
      for (runnable in scheduledRunnables) {
        runnable.run()
      }
      if (!project.isDisposed) {
        CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(viewProvider.virtualFile)
      }
      computation.reset()
      LOG.debug { "Computation finished normally for ${viewProvider.virtualFile.name}" }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val that = other as AsyncComputation
      return project == that.project
    }
  }

  private val psiFile: PsiFile?
    get() = viewProvider.getPsi(viewProvider.baseLanguage)

  // Check provider equivalence by file ref. Other fields make no sense since AsyncComputation is a stateful object
  // whose state (active=true->false) changes over time due to long computation.
  override fun equals(other: Any?): Boolean {
    return other is CodeStyleCachedValueProvider && viewProvider == other.viewProvider
  }
}


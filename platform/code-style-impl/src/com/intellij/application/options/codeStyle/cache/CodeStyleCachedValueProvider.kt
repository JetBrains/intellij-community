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
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
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
import java.util.function.Supplier

private val LOG = logger<CodeStyleCachedValueProvider>()

@Service(Service.Level.PROJECT)
private class CodeStyleCachedValueProviderService(@JvmField val coroutineScope: CoroutineScope)

internal class CodeStyleCachedValueProvider(val fileSupplier: Supplier<VirtualFile>,
                                            private val project: Project,
                                            private val dataHolder: UserDataHolder) : CachedValueProvider<CodeStyleSettings?> {
  private val file get() = fileSupplier.get()
  private val computation = AsyncComputation(project)
  private val computationLock: Lock = object : ReentrantLock() {
    override fun equals(other: Any?): Boolean = other is ReentrantLock
  }

  val isExpired: Boolean
    get() = computation.isExpired

  fun tryGetSettings(): CodeStyleSettings? {
    if (computationLock.tryLock()) {
      try {
        return CachedValuesManager.getManager(project).getCachedValue(dataHolder, this)
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
      LOG.debug { "Computation was cancelled for ${file.name}" }
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
    dependencies.add(ModificationTracker { if (file.isValid) 0 else 1 })
    return ArrayUtil.toObjectArray(dependencies)
  }

  private fun logCached(settings: CodeStyleSettings) {
    LOG.debug { String.format(
      "File: %s (%s), cached: %s, tracker: %d", file.name, Integer.toHexString(file.hashCode()),
      settings, settings.modificationTracker.modificationCount) }
  }

  /**
   * Always contains some result that can be obtained by `getCurrResult()` method.
   * Listeners are notified after the computation is finished and `getCurrResult()` contains a stable computed value.
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
        LOG.debug { "async for ${file.name}" }
        job = project.service<CodeStyleCachedValueProviderService>().coroutineScope.launch(
          CoroutineName(this@CodeStyleCachedValueProvider.toString())
        ) {
          val success = readAction {
            computeSettings()
          }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            // need to fix clients and remove global lock from there
            // maybe readAction
            writeIntentReadAction {
              notifyCachedValueComputed(success)
            }
          }
        }
      }
      else {
        LOG.debug { "sync for ${file.name}" }
        val success = app.runReadAction<Boolean>(::computeSettings)
        if (app.isDispatchThread) {
          notifyCachedValueComputed(success)
        }
        else {
          project.service<CodeStyleCachedValueProviderService>().coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            notifyCachedValueComputed(success)
          }
        }
      }
    }

    fun cancel() {
      LOG.debug { "expire computation for ${file.name}" }
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

    /**
     * @return true if settings were computed successfully, false otherwise
     */
    private fun computeSettings(): Boolean {
      val file = file
      val psiFile = psiFile
      // If the psiFile is added and deleted in the same write action,
      // it might still be present, but invalid.
      if (psiFile == null || !psiFile.isValid) {
        cancel()
        return false
      }

      computationLock.lock()
      try {
        LOG.debug { "Computation started for ${file.name}" }
        var currSettings = getCurrentSettings(psiFile)
        oldTrackerSetting = currSettings.modificationTracker.modificationCount
        @Suppress("TestOnlyProblems")
        if (currSettings !== settingsManager.temporarySettings) {
          val modifiableSettings = TransientCodeStyleSettings(this@CodeStyleCachedValueProvider.file, project, currSettings)
          modifiableSettings.applyIndentOptionsFromProviders(project, file)
          LOG.debug { "Created TransientCodeStyleSettings for ${file.name}" }
          for (modifier in CodeStyleSettingsModifier.EP_NAME.extensionList) {
            LOG.debug { "Modifying ${file.name}: ${modifier.javaClass.name}" }
            if (modifier.modifySettingsAndUiCustomization(modifiableSettings, psiFile) || modifiableSettings.modifier != null) {
              LOG.debug { "Modified ${file.name}: ${modifier.javaClass.name}" }
              currSettings = modifiableSettings
            }
            if (modifiableSettings.modifier != null) {
              LOG.debug { "Indenter for ${file.name}: ${modifier.javaClass.name}" }
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
      return true
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSettings(file: PsiFile): CodeStyleSettings {
      val result = FileCodeStyleProvider.EP_NAME.computeSafeIfAny { it.getSettings(file) }
      return result ?: settingsManager.currentSettings
    }

    fun getCurrentResult(): CodeStyleSettings? {
      if (isActive.compareAndSet(false, true)) {
        LOG.debug { "Computation initiated for ${file.name}" }
        start()
      }
      return currentResult
    }

    fun reset() {
      scheduledRunnables.clear()
      isActive.set(false)
      LOG.debug { "Computation reset for ${file.name}" }
    }

    private fun notifyCachedValueComputed(shouldFireEvent: Boolean = true) {
      @Suppress("DEPRECATION")
      val currentProjectSettings = settingsManager.currentSettings
      val newTrackerSetting = currentProjectSettings.modificationTracker.modificationCount
      if (oldTrackerSetting < newTrackerSetting && !insideRestartedComputation) {
        insideRestartedComputation = true
        try {
          LOG.debug { "restarted for ${file.name}" }
          start()
        }
        finally {
          insideRestartedComputation = false
        }
        return
      }
      LOG.debug { "running scheduled runnables for ${file.name}" }
      for (runnable in scheduledRunnables) {
        runnable.run()
      }
      if (shouldFireEvent && !project.isDisposed) {
        /* IJPL-179136
         *
         * It is expected that CodeStyleSettingsListener implementations will access code style settings,
         * e.g., via CodeStyle.getSettings(file), when handling a codeStyleSettingsChanged event.
         *
         * It is wrong to send these events after the value has been computed:
         * if this computed value has already been evicted from the cache at that point,
         * the computation will restart and send the event again.
         * This may cause infinite loops of recomputation.
         *
         * A cache miss must only slow the system down, not break it.
         *
         * CodeStyleSettingsModifier implementors are required to fireCodeStyleSettingsChanged events themselves
         * if any TransientCodeStyleSettings dependencies change.
         */
        if (!TooFrequentCodeStyleComputationWatcher.getInstance(project).isTooHighEvictionRateDetected()
            && !Registry.`is`("disable.codeStyleSettingsChanged.events.on.settings.cached")) {
          val eventSettings = if (Registry.`is`("code.style.cache.change.events.include.settings")) currentResult else null
          settingsManager.fireCodeStyleSettingsChanged(file, eventSettings)
        }
      }
      computation.reset()
      LOG.debug { "Computation finished normally for ${file.name}" }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val that = other as AsyncComputation
      return project == that.project
    }

    override fun hashCode(): Int {
      return project.hashCode()
    }

    fun dumpState(sb: StringBuilder) {
      sb.append("isActive: ").append(isActive.get()).append('\n')
      sb.append("computation tracker: ").append(tracker.modificationCount).append('\n')
      sb.append("oldTrackerSetting: ").append(oldTrackerSetting).append('\n')
      sb.append("insideRestartedComputation: ").append(insideRestartedComputation).append('\n')
      sb.append("Current result: ").append(currentResult).append('\n')
      sb.append("Scheduled runnables: ").append(scheduledRunnables.size).append('\n')
      sb.append("Job: ").append(job).append('\n')
    }
  }

  private val psiFile: PsiFile?
    get() = if (file.isValid) PsiManager.getInstance(project).findFile(file) else null

  // Check provider equivalence by file ref. Other fields make no sense since AsyncComputation is a stateful object
  // whose state (active=true->false) changes over time due to long computation.
  override fun equals(other: Any?): Boolean {
    return other is CodeStyleCachedValueProvider && file == other.file
  }

  override fun hashCode(): Int {
    return file.hashCode()
  }

  override fun toString(): String {
    return "CodeStyleCachedValueProvider@${Integer.toHexString(super.hashCode())}(file=$file)"
  }

  fun dumpState(sb: StringBuilder) {
    sb.append("File: ").append(file.toString()).append('\n')
    sb.append("file.isValid == ").append(file.isValid).append('\n')
    sb.append("Computation expired: ").append(isExpired).append('\n')
    sb.append("Computation:\n")
    computation.dumpState(sb)
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
@Service(Service.Level.APP)
class ProgressSoundSignalTracker(private val scope: CoroutineScope) {
  private val initialDelay = registryValue(INITIAL_DELAY_REGISTRY_KEY, 5000)
  private val repeatDelay = registryValue(REPEAT_DELAY_REGISTRY_KEY, 10000)

  private class TrackedProgress(val project: Project?, val model: ProgressModel, val info: TaskInfo) {
    fun isOver(): Boolean = model.isFinished(info) || project?.isDisposed == true
  }

  private val lock = Any()
  private val progresses = ArrayList<TrackedProgress>()
  private var timerJob: Job? = null

  internal fun track(project: Project?, model: ProgressModel, info: TaskInfo) {
    val progress = TrackedProgress(project, model, info)
    update { add(progress) }
    model.addOnFinishAction { if (it == info) update { remove(progress) } }
    if (progress.isOver()) update { remove(progress) }
  }

  private fun update(change: MutableList<TrackedProgress>.() -> Unit) {
    synchronized(lock) {
      progresses.change()
      if (progresses.isEmpty()) {
        timerJob?.cancel()
        timerJob = null
      }
      else if (timerJob == null) {
        timerJob = scope.launch { playWhileBusy() }
      }
    }
  }

  @get:TestOnly
  internal val isTimerRunning: Boolean
    get() = synchronized(lock) { timerJob != null }

  private suspend fun playWhileBusy() {
    delay(initialDelay)
    while (true) {
      if (!isProgressSignalOn()) {
        update { clear() }
        return
      }
      currentSignal()?.let { SoundSignalPlayer.getInstance().play(it) }
      delay(repeatDelay)
    }
  }

  private suspend fun currentSignal(): SoundSignal? {
    return withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      val (over, running) = synchronized(lock) { progresses.toList() }.partition { it.isOver() }
      if (over.isNotEmpty()) update { removeAll(over) }
      val active = ProjectUtil.getActiveProject()
      val progress = running.firstOrNull { it.project != null && it.project === active } ?: running.firstOrNull()
      progress?.model?.let { progressSoundSignal(it.isIndeterminate(), it.getFraction()) }
    }
  }

  companion object {
    internal fun getInstanceIfEnabled(): ProgressSoundSignalTracker? {
      val app = ApplicationManager.getApplication() ?: return null
      if (app.isDisposed || !isProgressSignalOn()) return null
      return app.service()
    }
  }
}

internal fun progressSoundSignal(indeterminate: Boolean, fraction: Double): SoundSignal = when {
  indeterminate -> IdeSoundSignals.PROGRESS_INDETERMINATE
  fraction < 1.0 / 3 -> IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_1
  fraction < 2.0 / 3 -> IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_2
  else -> IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_3
}

internal const val INITIAL_DELAY_REGISTRY_KEY: String = "ide.sound.signals.progress.initial.delay.ms"
internal const val REPEAT_DELAY_REGISTRY_KEY: String = "ide.sound.signals.progress.repeat.delay.ms"

private val PROGRESS_SIGNALS = listOf(
  IdeSoundSignals.PROGRESS_INDETERMINATE,
  IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_1,
  IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_2,
  IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_3,
)

private fun isProgressSignalOn(): Boolean = PROGRESS_SIGNALS.any(::isSoundSignalOn)

private fun registryValue(key: String, defaultValue: Int): Duration =
  RegistryManager.getInstance().intValue(key, defaultValue).coerceAtLeast(0).milliseconds

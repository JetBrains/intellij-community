// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.ui.IconDeferrer
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class TimedIconCache {
  private val idToIcon = HashMap<String, Icon>()
  private val idToInvalid = HashMap<String, Boolean>()
  private val iconCheckTimes = Object2LongOpenHashMap<String>()
  private val iconCalcTime = Object2LongOpenHashMap<String>()

  private val lock = ReentrantReadWriteLock()

  init {
    iconCheckTimes.defaultReturnValue(-1)
    iconCalcTime.defaultReturnValue(-1)
  }

  fun remove(id: String) {
    lock.write {
      idToIcon.remove(id)
      iconCheckTimes.removeLong(id)
      iconCalcTime.removeLong(id)
    }
  }

  fun get(id: String, settings: RunnerAndConfigurationSettings, project: Project): Icon {
    return lock.read { idToIcon.get(id) } ?: lock.write {
      idToIcon.get(id)?.let {
        return it
      }

      val icon = deferIcon(id, settings.configuration.icon, project.hashCode() xor settings.hashCode(), project)
      set(id, icon)
      icon
    }
  }

  private fun deferIcon(id: String, baseIcon: Icon?, hash: Int, project: Project): Icon {
    val projectRef = WeakReference(project)
    return IconDeferrer.getInstance().deferAutoUpdatable(baseIcon, hash) {
      @Suppress("NAME_SHADOWING") val project = projectRef.get()
      if (project == null || project.isDisposed) {
        return@deferAutoUpdatable null
      }

      lock.write {
        iconCalcTime.removeLong(id)
      }

      val startTime = System.currentTimeMillis()
      val iconToValid = try {
        calcIcon(id, baseIcon, RunManagerImpl.getInstanceImpl(project))
      }
      catch (e: ProcessCanceledException) {
        return@deferAutoUpdatable null
      }

      lock.write {
        iconCalcTime.put(id, System.currentTimeMillis() - startTime)
        idToInvalid.put(id, iconToValid.second)
      }
      return@deferAutoUpdatable iconToValid.first
    }
  }

  fun isInvalid(id: String) : Boolean {
    idToInvalid.get(id)?.let {return it}
    return false
  }

  private fun calcIcon(id: String, baseIcon: Icon?, runManagerImpl: RunManagerImpl): Pair<Icon, Boolean> {
    val settings = runManagerImpl.getConfigurationById(id)
    if (settings == null) {
      return (baseIcon ?: AllIcons.Actions.Help) to false
    }

    try {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(runManagerImpl.project, Runnable {
        settings.checkSettings()
      })
      return ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
    }
    catch (e: IndexNotReadyException) {
      return ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
    }
    catch (ignored: RuntimeConfigurationException) {
      val invalid = !DumbService.isDumb(runManagerImpl.project)
      return ProgramRunnerUtil.getConfigurationIcon(settings, invalid) to invalid
    }
  }

  private fun set(id: String, icon: Icon) {
    idToIcon.put(id, icon)
    iconCheckTimes.put(id, System.currentTimeMillis())
  }

  fun clear() {
    lock.write {
      idToIcon.clear()
      iconCheckTimes.clear()
      iconCalcTime.clear()
    }
  }

  fun checkValidity(id: String) {
    lock.read {
      val lastCheckTime = iconCheckTimes.getLong(id)
      var expired = lastCheckTime == -1L
      if (!expired) {
        var calcTime = iconCalcTime.getLong(id)
        if (calcTime == -1L || calcTime < 150) {
          calcTime = 150L
        }
        expired = (System.currentTimeMillis() - lastCheckTime) > (calcTime * 10)
      }

      if (expired) {
        lock.write {
          idToIcon.remove(id)
        }
      }
    }
  }
}
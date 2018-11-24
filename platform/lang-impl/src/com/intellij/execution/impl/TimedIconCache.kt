/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconDeferrer
import com.intellij.util.containers.ObjectLongHashMap
import gnu.trove.THashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write

class TimedIconCache {
  private val idToIcon = THashMap<String, Icon>()
  private val iconCheckTimes = ObjectLongHashMap<String>()
  private val iconCalcTime = ObjectLongHashMap<String>()

  private val lock = ReentrantReadWriteLock()

  fun remove(id: String) {
    lock.write {
      idToIcon.remove(id)
      iconCheckTimes.remove(id)
      iconCalcTime.remove(id)
    }
  }

  fun get(id: String, settings: RunnerAndConfigurationSettings, project: Project): Icon {
    return lock.read { idToIcon.get(id) } ?: lock.write {
      idToIcon.get(id)?.let {
        return it
      }

      val icon = IconDeferrer.getInstance().deferAutoUpdatable(settings.configuration.icon, project.hashCode() xor settings.hashCode()) {
        if (project.isDisposed) {
          return@deferAutoUpdatable null
        }

        lock.write {
          iconCalcTime.remove(id)
        }

        val startTime = System.currentTimeMillis()

        val icon = calcIcon(settings, project)

        lock.write {
          iconCalcTime.put(id, System.currentTimeMillis() - startTime)
        }

        icon
      }

      set(id, icon)
      icon
    }
  }

  private fun calcIcon(settings: RunnerAndConfigurationSettings, project: Project): Icon {
    try {
      settings.checkSettings()
      return ProgramRunnerUtil.getConfigurationIcon(settings, false)
    }
    catch (e: IndexNotReadyException) {
      return ProgramRunnerUtil.getConfigurationIcon(settings, false)
    }
    catch (ignored: RuntimeConfigurationException) {
      return ProgramRunnerUtil.getConfigurationIcon(settings, !DumbService.isDumb(project))
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
      val lastCheckTime = iconCheckTimes.get(id)
      var expired = lastCheckTime == -1L
      if (!expired) {
        var calcTime = iconCalcTime.get(id)
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
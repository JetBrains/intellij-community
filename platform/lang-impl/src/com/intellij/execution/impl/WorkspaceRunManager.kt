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

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jdom.Element

class WorkspaceRunManager(project: Project, propertiesComponent: PropertiesComponent) : RunManagerImpl(project, propertiesComponent) {
  override fun loadState(parentNode: Element) {
    clear(false)

    schemeManagerProvider.load(parentNode) {
      var name = it.getAttributeValue("name")
      if (name == "<template>" || name == null) {
        // scheme name must be unique
        it.getAttributeValue("type")?.let {
          if (name == null) {
            name = "<template>"
          }
          name += " of type ${it}"
        }
      }
      name
    }
    schemeManager.reload()

    super.loadState(parentNode)
  }

  override fun removeConfiguration(settings: RunnerAndConfigurationSettings?) {
    if (settings == null) {
      return
    }

    val it = sortedConfigurations.iterator()
    for (configuration in it) {
      if (configuration === settings) {
        if (mySelectedConfigurationId != null && mySelectedConfigurationId === settings.uniqueID) {
          selectedConfiguration = null
        }

        it.remove()
        mySharedConfigurations.remove(settings.uniqueID)
        myRecentlyUsedTemporaries.remove(settings.configuration)
        myDispatcher.multicaster.runConfigurationRemoved(configuration)
      }

      val beforeRunTaskIterator = (configuration.configuration as? RunConfigurationBase)?.beforeRunTasks?.iterator() ?: continue
      for (task in beforeRunTaskIterator) {
        if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask && task.settings === settings) {
          beforeRunTaskIterator.remove()
          myDispatcher.multicaster.runConfigurationChanged(configuration, null)
        }
      }
    }
  }
}
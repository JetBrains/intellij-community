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

import com.intellij.execution.IS_RUN_MANAGER_INITIALIZED
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.UnknownRunConfiguration
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.Pair
import gnu.trove.THashSet
import org.jdom.Element

internal class ProjectRunConfigurationInitializer(project: Project) {
  init {
    val connection = project.messageBus.connect()
    connection.subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      override fun projectComponentsInitialized(eventProject: Project) {
        if (project === eventProject) {
          requestLoadWorkspaceAndProjectRunConfiguration(project)
        }
      }
    })
  }

  private fun requestLoadWorkspaceAndProjectRunConfiguration(project: Project) {
    if (IS_RUN_MANAGER_INITIALIZED.get(project) == true) {
      return
    }

    IS_RUN_MANAGER_INITIALIZED.set(project, true)
    // we must not fire beginUpdate here, because message bus will fire queued parent message bus messages (and, so, SOE may occur because all other projectOpened will be processed before us)
    // simply, you should not listen changes until project opened
    if (isUseProjectSchemeManager()) {
      project.service<RunManager>()
    }
    else {
      project.service<ProjectRunConfigurationManager>()
    }
  }
}

@State(name = "ProjectRunConfigurationManager", storages = arrayOf(Storage(value = "runConfigurations", stateSplitter = OldProjectRunConfigurationStateSplitter::class)))
private class ProjectRunConfigurationManager(manager: RunManager) : PersistentStateComponent<Element> {
  private val manager = manager as RunManagerImpl

  override fun getState(): Element? {
    val state = Element("state")
    manager.writeConfigurations(state, manager.getSharedConfigurations())
    return state
  }

  override fun loadState(state: Element) {
    val existing = THashSet<String>()
    state.getChildren(RunManagerImpl.CONFIGURATION).mapTo(existing) {
      manager.loadConfiguration(it, true).uniqueID
    }

    manager.removeNotExistingSharedConfigurations(existing)
    manager.requestSort()

    if (manager.selectedConfiguration == null) {
      for (settings in manager.allSettings) {
        if (settings.type !is UnknownRunConfiguration) {
          manager.selectedConfiguration = settings
          break
        }
      }
    }
  }
}

internal class OldProjectRunConfigurationStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): List<Pair<Element, String>> = StateSplitterEx.splitState(state, RunManagerImpl.NAME_ATTR)
}
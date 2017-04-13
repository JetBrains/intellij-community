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

import com.intellij.execution.configurations.UnknownRunConfiguration
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Pair
import gnu.trove.THashSet
import org.jdom.Element

@State(name = "ProjectRunConfigurationManager", storages = arrayOf(Storage(value = "runConfigurations", stateSplitter = ProjectRunConfigurationManager.RunConfigurationStateSplitter::class)))
class ProjectRunConfigurationManager(private val manager: RunManagerImpl) : PersistentStateComponent<Element> {

  override fun getState(): Element? {
    val state = Element("state")
    manager.writeConfigurations(state, manager.getSharedConfigurations())
    return state
  }

  override fun loadState(state: Element) {
    val existing = THashSet<String>()
    for (child in state.getChildren(RunManagerImpl.CONFIGURATION)) {
      existing.add(manager.loadConfiguration(child, true).uniqueID)
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

  internal class RunConfigurationStateSplitter : StateSplitterEx() {
    override fun splitState(state: Element): List<Pair<Element, String>> {
      return StateSplitterEx.splitState(state, RunManagerImpl.NAME_ATTR)
    }
  }
}

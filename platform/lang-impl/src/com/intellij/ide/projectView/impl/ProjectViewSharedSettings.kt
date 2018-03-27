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
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ProjectViewSharedSettings",
       storages = arrayOf(Storage(value = "projectView.xml")))

class ProjectViewSharedSettings : PersistentStateComponent<ProjectViewSharedSettings> {
  var flattenPackages = false
  var showMembers = false
  var sortByType = false
  var showModules = true
  var flattenModules = false
  var showLibraryContents = true
  var hideEmptyPackages = true
  var abbreviatePackages = false
  var autoscrollFromSource = false
  var autoscrollToSource = false
  var foldersAlwaysOnTop = true
  var manualOrder = false

  override fun getState(): ProjectViewSharedSettings? {
    return this
  }

  override fun loadState(state: ProjectViewSharedSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    val instance: ProjectViewSharedSettings
      get() = ServiceManager.getService(ProjectViewSharedSettings::class.java)
  }
}

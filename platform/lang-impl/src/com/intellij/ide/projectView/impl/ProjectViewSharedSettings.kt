// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ProjectViewSharedSettings", storages = [(Storage(value = "projectView.xml"))])
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

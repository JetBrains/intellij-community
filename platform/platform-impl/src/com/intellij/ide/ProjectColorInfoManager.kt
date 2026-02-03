// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Service(Service.Level.PROJECT)
@State(name = "ProjectColorInfo", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
internal class ProjectColorInfoManager : SerializablePersistentStateComponent<ProjectColorInfo>(ProjectColorInfo()) {
  companion object {
    fun getInstance(project: Project): ProjectColorInfoManager = project.service()
  }

  var customColor: String?
    get() = state.customColor
    set(value) {
      updateState {
        it.copy(customColor = value)
      }
    }

  var associatedIndex: Int?
    get() = state.associatedIndex
    set(value) {
      updateState {
        it.copy(associatedIndex = value)
      }
    }

  val recentProjectColorInfo: RecentProjectColorInfo get() = RecentProjectColorInfo().also {
    it.customColor = this.customColor
    it.associatedIndex = this.associatedIndex ?: -1
  }
}

@Serializable
internal data class ProjectColorInfo(var customColor: String? = null,
                                     var associatedIndex: Int? = null)

@Internal
@Property(style = Property.Style.ATTRIBUTE)
class RecentProjectColorInfo : BaseState() {
  var customColor: String? by string("")
  var associatedIndex: Int by property(-1)
}
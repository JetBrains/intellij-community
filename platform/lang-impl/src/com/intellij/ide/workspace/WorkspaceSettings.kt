// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@get:ApiStatus.Internal
val Project.isWorkspace get() = getWorkspaceSettings(this).isWorkspace

@VisibleForTesting
@ApiStatus.Internal
fun setWorkspace(project: Project) {
  getWorkspaceSettings(project).isWorkspace = true
}

internal fun getWorkspaceSettings(project: Project) = project.service<WorkspaceSettings>()

@Service(Service.Level.PROJECT)
@State(name = "WorkspaceSettings", storages = [Storage("jb-workspace.xml")])
internal class WorkspaceSettings : BaseState(), PersistentStateComponent<WorkspaceSettings> {
  override fun getState(): WorkspaceSettings = this
  override fun loadState(state: WorkspaceSettings) {
    copyFrom(state)
  }

  var isWorkspace: Boolean by property(false)
  @get:XCollection
  @get:Property(surroundWithTag = false)
  var subprojects: MutableList<SubprojectSettings> by list()
}

@Tag("project")
@Property(style = Property.Style.ATTRIBUTE)
internal class SubprojectSettings(): BaseState() {
  constructor(name: String, path: String) : this() {
    this.name = name
    this.path = path
  }

  var name by string()
  var path by string()
}

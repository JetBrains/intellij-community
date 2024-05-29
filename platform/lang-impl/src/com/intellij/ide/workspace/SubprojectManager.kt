// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.workspace.SubprojectHandler.Companion.EP_NAME
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class SubprojectManager(private val project: Project) {
  fun getAllSubprojects(): List<Subproject> {
    val subprojects = EP_NAME.extensionList.flatMap { it.getSubprojects(project) }
    return subprojects
  }
}

internal fun getAllSubprojects(project: Project): List<Subproject> {
  return project.service<SubprojectManager>().getAllSubprojects()
}
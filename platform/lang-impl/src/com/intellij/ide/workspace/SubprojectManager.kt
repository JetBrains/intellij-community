// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.workspace.SubprojectHandler.Companion.EP_NAME
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

fun getSubprojectByModule(module: Module): Subproject? {
  return getSubprojectManager(module.project).getSubprojectByModule(module)
}

@Service(Service.Level.PROJECT)
internal class SubprojectManager(private val workspace: Project) {

  private val subprojects: CachedValue<Map<String, Subproject>> = CachedValuesManager.getManager(workspace).createCachedValue {
    val subprojects = EP_NAME.extensionList
      .flatMap { it.getSubprojects(workspace) }
      .associateBy { it.projectPath }
    return@createCachedValue CachedValueProvider.Result.create(subprojects, ProjectRootModificationTracker.getInstance(workspace))
  }

  fun getAllSubprojects(): Collection<Subproject> = subprojects.value.values

  fun getSubprojectByModule(module: Module): Subproject? {
    return getPath(module)?.let { subprojects.value[it] }
  }

  private fun getPath(module: Module): String? {
    return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path
  }
}

internal fun getSubprojectManager(project: Project) = project.service<SubprojectManager>()

internal fun getAllSubprojects(project: Project): Collection<Subproject> {
  return getSubprojectManager(project).getAllSubprojects()
}
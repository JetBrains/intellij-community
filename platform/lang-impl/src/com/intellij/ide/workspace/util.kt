// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class MyCoroutineScopeService(val scope: CoroutineScope)

internal fun getCoroutineScope(workspace: Project) = workspace.service<MyCoroutineScopeService>().scope

internal fun removeSubprojects(subprojects: Collection<Subproject>) {
  val first = subprojects.firstOrNull() ?: return
  val removed = subprojects.groupBy { it.handler }
  removed.forEach {
    it.key.removeSubprojects(it.value)
  }
}
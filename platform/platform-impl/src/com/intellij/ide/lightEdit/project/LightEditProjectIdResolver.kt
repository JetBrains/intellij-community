// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.ProjectIdResolver
import com.intellij.platform.project.projectId

private class LightEditProjectIdResolver : ProjectIdResolver {
  override fun resolve(id: ProjectId): Project? {
    val lightEditProject = LightEditUtil.getProjectIfCreated() ?: return null
    return lightEditProject.takeIf { it.projectId() == id }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspace

import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
class ExternalSubproject(val projectInfo: ExternalProjectInfo, override val handler: SubprojectHandler) : Subproject {

  override val name: String
    get() = projectInfo.externalProjectStructure?.data?.externalName
            ?: FileUtil.getNameWithoutExtension(projectPath)
  override val projectPath: String get() = projectInfo.externalProjectPath
}
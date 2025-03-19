// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class RunConfigurationVcsSupport {
    open fun hasActiveVcss(project: Project): Boolean = false
    open fun isDirectoryVcsIgnored(project: Project, path: String): Boolean = false
}
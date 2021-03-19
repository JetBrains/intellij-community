// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project

fun ExternalSystemUnlinkedProjectAware.linkAndLoadProjectWithLoadingConfirmation(project: Project, externalProjectPath: String) {
  ExternalSystemUtil.confirmLoadingUntrustedProject(project, systemId)
  linkAndLoadProject(project, externalProjectPath)
}

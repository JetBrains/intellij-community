// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.project.Project


fun ExternalSystemUnlinkedProjectAware.getProjectId(externalProjectPath: String): ExternalSystemProjectId {
  return ExternalSystemProjectId(systemId, externalProjectPath)
}

fun createExtensionDisposable(project: Project, unlinkedProjectAware: ExternalSystemUnlinkedProjectAware): Disposable {
  return ExternalSystemUnlinkedProjectAware.EP_NAME.createExtensionDisposable(unlinkedProjectAware, project)
}
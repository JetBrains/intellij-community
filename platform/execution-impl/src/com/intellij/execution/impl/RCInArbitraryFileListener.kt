// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.configurationStore.XmlProjectFileListener
import com.intellij.openapi.project.Project

internal class RCInArbitraryFileListener : XmlProjectFileListener(".run.xml", RCInArbitraryFileManager::class.java) {
  override fun updateFiles(project: Project, deletedFilePaths: Collection<String>, updatedFilePaths: Collection<String>) {
    if (!isRunConfigsFromArbitraryFilesEnabled()) {
      return
    }
    RunManagerImpl.getInstanceImpl(project).updateRunConfigsFromArbitraryFiles(deletedFilePaths, updatedFilePaths)
  }
}

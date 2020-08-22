// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createOrLoadProject
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

abstract class LoadProjectBase {

  abstract val tempDirectory: TemporaryDirectory
  abstract val testDataRoot: Path


  protected fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(testDataRoot.resolve(testDataDirName), checkProject)
  }

  protected fun loadProjectAndCheckResults(projectPath: Path, checkProject: suspend (Project) -> Unit) {
    @Suppress("RedundantSuspendModifier")
    suspend fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = VfsUtil.virtualToIoFile(dir)
      FileUtil.copyDir(projectPath.toFile(), projectDir)
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir.toPath()
    }
    runBlocking {
      createOrLoadProject(tempDirectory, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) {
        checkProject(it)
      }
    }
  }
}


// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.createFile
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.nullize
import org.junit.rules.TestWatcher
import org.junit.runner.Description

private const val EXCLUDED_DIR_NAME = "excludedDir"

internal class TestManager(private val projectRule: ProjectRule, private val tempDirManager: TemporaryDirectory) : TestWatcher() {
  var annotation: TestDescriptor? = null

  var filePath: String? = null

  @Target(AnnotationTarget.FUNCTION)
  annotation class TestDescriptor(val filePath: String,
                                  val line: Int = -1,
                                  val column: Int = -1,
                                  val relativeToProject: Boolean = false,
                                  val excluded: Boolean = false,
                                  val doNotCreate: Boolean = false,
                                  val status: Int = 200)

  override fun starting(description: Description) {
    annotation = description.getAnnotation(TestDescriptor::class.java) ?: return
    filePath = annotation!!.filePath.nullize() ?: return

    // trigger project creation
    projectRule.project

    if (filePath!! == "_tmp_") {
      val file = tempDirManager.newPath(".txt")
      if (!annotation!!.doNotCreate) {
        file.createFile()
      }
      filePath = file.systemIndependentPath
      return
    }

    if (annotation!!.doNotCreate) {
      return
    }

    runInEdtAndWait {
      val normalizedFilePath = FileUtilRt.toSystemIndependentName(filePath!!)
      if (annotation!!.relativeToProject) {
        val root = PlatformTestUtil.getOrCreateProjectBaseDir(projectRule.project)
        runWriteAction {
          root.findOrCreateChildData(this@TestManager, normalizedFilePath)
        }
      }
      else {
        val module = projectRule.module
        if (annotation!!.excluded) {
          ModuleRootModificationUtil.updateModel(module) { model ->
            val contentEntry = model.contentEntries[0]
            val contentRoot = contentEntry.file!!
            runWriteAction {
              contentRoot.findChild(EXCLUDED_DIR_NAME)?.delete(this@TestManager)
              val dir = contentRoot.createChildDirectory(this@TestManager, EXCLUDED_DIR_NAME)
              dir.createChildData(this@TestManager, normalizedFilePath)
              contentEntry.addExcludeFolder(dir)
            }
          }

          filePath = "$EXCLUDED_DIR_NAME/$filePath"
        }
        else {
          val root = ModuleRootManager.getInstance(module).sourceRoots[0]
          runWriteAction {
            root.findOrCreateChildData(this@TestManager, normalizedFilePath)
          }
        }
      }
    }
  }

  override fun finished(description: Description?) {
    if (annotation?.excluded == true) {
      ModuleRootModificationUtil.updateModel(projectRule.module) { model -> model.contentEntries[0].removeExcludeFolder(EXCLUDED_DIR_NAME) }
    }

    annotation = null
    filePath = null
  }
}
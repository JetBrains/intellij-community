package org.jetbrains.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.createFile
import com.intellij.util.systemIndependentPath
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

private val EXCLUDED_DIR_NAME = "excludedDir"

internal class TestManager(val projectRule: ProjectRule, private val tempDirManager: TemporaryDirectory) : TestWatcher() {
  var annotation: TestDescriptor? = null

  var filePath: String? = null
  private var fileToDelete: VirtualFile? = null

  private var ioFileToDelete: File? = null

  @Target(AnnotationTarget.FUNCTION)
  annotation class TestDescriptor(val filePath: String,
                                  val line: Int = -1,
                                  val column: Int = -1,
                                  val relativeToProject: Boolean = false,
                                  val excluded: Boolean = false,
                                  val doNotCreate: Boolean = false,
                                  val status: Int = 200)

  override fun starting(description: Description) {
    annotation = description.getAnnotation(TestDescriptor::class.java)
    if (annotation == null) {
      return
    }

    filePath = StringUtil.nullize(annotation!!.filePath)
    if (filePath == null) {
      return
    }

    // trigger project creation
    projectRule.project

    if (filePath!! == "_tmp_") {
      val file = tempDirManager.newPath(".txt", refreshVfs = true)
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
        val root = projectRule.project.baseDir
        runWriteAction {
          fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
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
              fileToDelete = contentRoot.createChildDirectory(this@TestManager, EXCLUDED_DIR_NAME)
              fileToDelete!!.createChildData(this@TestManager, normalizedFilePath)
            }
            contentEntry.addExcludeFolder(fileToDelete!!)
          }

          filePath = "$EXCLUDED_DIR_NAME/$filePath"
        }
        else {
          val root = ModuleRootManager.getInstance(module).sourceRoots[0]
          runWriteAction {
            fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
          }
        }
      }
    }
  }

  override fun finished(description: Description?) {
    if (annotation!!.excluded) {
      ModuleRootModificationUtil.updateModel(projectRule.module) { model -> model.contentEntries[0].removeExcludeFolder(EXCLUDED_DIR_NAME) }
    }

    if (fileToDelete != null) {
      runInEdtAndWait { runWriteAction { fileToDelete?.delete(this@TestManager) } }
      fileToDelete = null
    }

    if (ioFileToDelete != null && !FileUtilRt.delete(ioFileToDelete!!)) {
      ioFileToDelete!!.deleteOnExit()
    }

    annotation = null
    filePath = null
  }
}
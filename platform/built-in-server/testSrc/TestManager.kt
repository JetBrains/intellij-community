package org.jetbrains.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class TestManager(val projectRule: ProjectRule, private val tempDirManager: TemporaryDirectory) : TestWatcher() {
  companion object {
    private val EXCLUDED_DIR_NAME = "excludedDir"
  }

  var annotation: TestDescriptor? = null

  var filePath: String? = null
  private var fileToDelete: VirtualFile? = null

  private var ioFileToDelete: File? = null

  Retention(RetentionPolicy.RUNTIME)
  Target(ElementType.METHOD)
  annotation public class TestDescriptor(public val filePath: String,
                                         public val line: Int = -1,
                                         public val column: Int = -1,
                                         public val relativeToProject: Boolean = false,
                                         public val excluded: Boolean = false,
                                         public val doNotCreate: Boolean = false,
                                         public val status: Int = 200)

  override fun starting(description: Description) {
    annotation = description.getAnnotation(javaClass<TestDescriptor>())
    if (annotation == null) {
      return
    }

    filePath = StringUtil.nullize(annotation!!.filePath)
    if (filePath == null) {
      return
    }

    // trigger project creation
    runInEdtAndWait {
      projectRule.project
    }

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
        val root = projectRule.project.getBaseDir()
        runWriteAction {
          fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
        }
      }
      else {
        val module = projectRule.module
        if (annotation!!.excluded) {
          ModuleRootModificationUtil.updateModel(module) { model ->
            val contentEntry = model.getContentEntries()[0]
            val contentRoot = contentEntry.getFile()!!
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
          val root = ModuleRootManager.getInstance(module).getSourceRoots()[0]
          runWriteAction {
            fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
          }
        }
      }
    }
  }

  override fun finished(description: Description?) {
    if (annotation!!.excluded) {
      ModuleRootModificationUtil.updateModel(projectRule.module) { model -> model.getContentEntries()[0].removeExcludeFolder(EXCLUDED_DIR_NAME) }
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
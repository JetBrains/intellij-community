package org.jetbrains.ide

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class TestManager(val projectFixture: IdeaProjectTestFixture) : TestWatcher() {
  companion object {
    val EXCLUDED_DIR_NAME = "excludedDir"
  }

  public var annotation: TestDescriptor? = null

  public var filePath: String? = null
  private var fileToDelete: VirtualFile? = null

  private var ioFileToDelete: File? = null

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  annotation public class TestDescriptor(public val filePath: String,
                                         public val line: Int = -1,
                                         public val column: Int = -1,
                                         public val relativeToProject: Boolean = false,
                                         public val excluded: Boolean = false,
                                         public val doNotCreate: Boolean = false,
                                         public val status: Int = 200)

  override fun starting(description: Description) {
    annotation = description.getAnnotation<TestDescriptor>(javaClass<TestDescriptor>())
    if (annotation == null) {
      return
    }

    filePath = StringUtil.nullize(annotation!!.filePath)
    if (filePath == null) {
      return
    }

    if (filePath!! == "_tmp_") {
      if (annotation!!.doNotCreate) {
        filePath = FileUtilRt.generateRandomTemporaryPath().getPath()
      }
      else {
        ioFileToDelete = FileUtilRt.createTempFile("testFile", ".txt")
        filePath = ioFileToDelete!!.getPath()
      }
      return
    }

    if (annotation!!.doNotCreate) {
      return
    }

    invokeAndWaitIfNeed {
      val normalizedFilePath = FileUtilRt.toSystemIndependentName(filePath!!)
      if (annotation!!.relativeToProject) {
        val root = projectFixture.getProject().getBaseDir()
        runWriteAction {
          fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
        }
      }
      else {
        if (annotation!!.excluded) {
          ModuleRootModificationUtil.updateModel(projectFixture.getModule()) { model ->
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
          val root = ModuleRootManager.getInstance(projectFixture.getModule()).getSourceRoots()[0]
          runWriteAction {
            fileToDelete = root.findOrCreateChildData(this@TestManager, normalizedFilePath)
          }
        }
      }
    }
  }

  override fun finished(description: Description?) {
    if (annotation!!.excluded) {
      ModuleRootModificationUtil.updateModel(projectFixture.getModule()) { model -> model.getContentEntries()[0].removeExcludeFolder(EXCLUDED_DIR_NAME) }
    }

    if (fileToDelete != null) {
      invokeAndWaitIfNeed { runWriteAction { fileToDelete?.delete(this@TestManager) } }
      fileToDelete = null
    }

    if (ioFileToDelete != null && !FileUtilRt.delete(ioFileToDelete!!)) {
      ioFileToDelete!!.deleteOnExit()
    }

    annotation = null
    filePath = null
  }
}
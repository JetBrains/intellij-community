// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.PathUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString

private const val EXCLUDED_DIR_NAME = "excludedDir"

class TestManager(val annotation: TestDescriptor?, val filePath: String?) {
  @Target(AnnotationTarget.FUNCTION)
  annotation class TestDescriptor(
    val filePath: String,
    val line: Int = -1,
    val column: Int = -1,
    val relativeToProject: Boolean = false,
    val excluded: Boolean = false,
    val doNotCreate: Boolean = false,
    val status: Int = 200,
  )
}

internal fun testManagerFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<TestManager> = testFixture { context ->
  val annotation = context.findAnnotation(TestManager.TestDescriptor::class.java)
    ?: return@testFixture initialized(TestManager(null, null)) {}
  val rawFilePath = annotation.filePath.nullize()
    ?: return@testFixture initialized(TestManager(annotation, null)) {}
  if (rawFilePath == "_tmp_") {
    val tempDir = tempDirFixture.init()
    val file = tempDir.resolve("${sanitizeFileName(context.testName)}.txt")
    if (!annotation.doNotCreate) {
      withContext(Dispatchers.IO) {
        file.createParentDirectories().createFile()
      }
    }
    return@testFixture initialized(TestManager(annotation, file.invariantSeparatorsPathString)) {}
  }

  if (annotation.doNotCreate) {
    return@testFixture initialized(TestManager(annotation, rawFilePath)) {}
  }

  val project = projectFixture.init()
  val normalizedFilePath = FileUtilRt.toSystemIndependentName(rawFilePath)
  val parentPath = PathUtil.getParentPath(normalizedFilePath)
  val fileName = PathUtil.getFileName(normalizedFilePath)

  if (annotation.relativeToProject) {
    val root = withContext(Dispatchers.IO) {
      PlatformTestUtil.getOrCreateProjectBaseDir(project)
    }
    val createdFile = edtWriteAction {
      val dir = if (parentPath.isEmpty()) root else VfsUtil.createDirectoryIfMissing(root, parentPath)
      dir.findOrCreateChildData(this@testFixture, fileName)
    }
    return@testFixture initialized(TestManager(annotation, rawFilePath)) {
      edtWriteAction {
        if (createdFile.isValid) {
          createdFile.delete(this@testFixture)
        }
      }
    }
  }

  val tempDir = tempDirFixture.init()
  val moduleDir = withContext(Dispatchers.IO) {
    tempDir.resolve("module").createDirectories()
  }
  val contentRoot = withContext(Dispatchers.IO) {
    requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(moduleDir)) {
      "Cannot find virtual directory for $moduleDir"
    }
  }
  val moduleManager = ModuleManager.getInstance(project)
  val resolvedFilePath = if (annotation.excluded) "$EXCLUDED_DIR_NAME/$rawFilePath" else rawFilePath
  val module = edtWriteAction {
    val module = moduleManager.newModule(moduleDir.resolve("test.iml"), EmptyModuleType.EMPTY_MODULE)
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val contentEntry = model.addContentEntry(contentRoot)
    if (annotation.excluded) {
      val excludedDir = contentRoot.createChildDirectory(this@testFixture, EXCLUDED_DIR_NAME)
      val targetDir = if (parentPath.isEmpty()) excludedDir else VfsUtil.createDirectoryIfMissing(excludedDir, parentPath)
      targetDir.findOrCreateChildData(this@testFixture, fileName)
      contentEntry.addExcludeFolder(excludedDir)
    }
    else {
      contentEntry.addSourceFolder(contentRoot, false)
      val targetDir = if (parentPath.isEmpty()) contentRoot else VfsUtil.createDirectoryIfMissing(contentRoot, parentPath)
      targetDir.findOrCreateChildData(this@testFixture, fileName)
    }
    model.commit()
    module
  }
  initialized(TestManager(annotation, resolvedFilePath)) {
    edtWriteAction {
      if (!module.isDisposed) {
        moduleManager.disposeModule(module)
      }
    }
  }
}

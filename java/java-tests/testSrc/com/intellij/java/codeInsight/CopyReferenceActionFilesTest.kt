/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.WEB_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CopyReferenceActionFilesTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  lateinit var module: Module
  lateinit var rootDir: VirtualFile
  
  private val psiManager: PsiManager
    get() = PsiManager.getInstance(projectModel.project)
  
  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction {
      rootDir = projectModel.baseProjectDir.newVirtualDirectory("root")
      module = projectModel.createModule()
      module.setModuleType(JavaModuleType.getModuleType().id)
    }
  }

  @Test
  fun `reference to file registered as source root must contain its name`() = runBlocking {
    // CPP-4315 "Edit | Copy Reference" result doesn't contain the file name
    lateinit var dir: VirtualFile
    lateinit var dirSubFile: VirtualFile
    lateinit var file: VirtualFile
    edtWriteAction {
      dir = rootDir.createChildDirectory(this, "dir")
      dirSubFile = dir.createChildData(this, "dir_subfile.txt")
      file = rootDir.createChildData(this, "file.txt")
      PsiTestUtil.addContentRoot(module, rootDir)
      PsiTestUtil.addSourceRoot(module, dir)
      PsiTestUtil.addSourceRoot(module, file)
    }
    readAction {
      assertEquals("dir", CopyReferenceAction.elementToFqn(psiManager.findDirectory(dir)))
      assertEquals("dir_subfile.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(dirSubFile)))
      assertEquals("file.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(file)))
    }
  }
  
  @Test
  fun `reference to file under non-java source root must include path from content root`() = runBlocking {
    lateinit var file: VirtualFile
    edtWriteAction {
      module.setModuleType(WEB_MODULE_ENTITY_TYPE_ID_NAME)
      val sourceRoot = rootDir.createChildDirectory(this, "src")
      PsiTestUtil.addContentRoot(module, rootDir)
      PsiTestUtil.addSourceRoot(module, sourceRoot)
      file = sourceRoot.createChildData(this, "file.txt")
    }
    readAction {
      assertEquals("src/file.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(file)))
    }
  }

  @Test
  fun `reference to file registered as content root must contain its full path`() = runBlocking {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    lateinit var dir: VirtualFile
    lateinit var dirSubFile: VirtualFile
    lateinit var file: VirtualFile
    edtWriteAction {
      dir = rootDir.createChildDirectory(this, "dir")
      dirSubFile = dir.createChildData(this, "dir_subfile.txt")
      file = rootDir.createChildData(this, "file.txt")
      PsiTestUtil.addContentRoot(module, dir)
      PsiTestUtil.addContentRoot(module, file)
    }
    readAction {
      assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(psiManager.findDirectory(dir)))
      assertEquals("dir_subfile.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(dirSubFile)))
      assertEquals(file.getPath(), CopyReferenceAction.elementToFqn(psiManager.findFile(file)))
    }
  }

  @Test
  fun `reference to file registered as nested content root must contain path from outer most root`() = runBlocking {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    lateinit var dir: VirtualFile
    lateinit var dirDir: VirtualFile
    lateinit var dirDirFile: VirtualFile
    edtWriteAction {
      dir = rootDir.createChildDirectory(this, "dir")
      dirDir = dir.createChildDirectory(this, "dir_dir")
      dirDirFile = dirDir.createChildData(this, "file.txt")
      PsiTestUtil.addContentRoot(module, dir)
      PsiTestUtil.addContentRoot(module, dirDir)
    }
    readAction {
      assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(psiManager.findDirectory(dir)))
      assertEquals("dir_dir", CopyReferenceAction.elementToFqn(psiManager.findDirectory(dirDir)))
      assertEquals("dir_dir/file.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(dirDirFile)))
    }
  }

  @Test
  fun `reference to file under exclude root must contain path from the corresponding content root`() = runBlocking {
    // IDEA-144316 Copy Reference should work for excluded subfolders same way as it works for regular project subdirs
    lateinit var dir: VirtualFile
    lateinit var dirDir: VirtualFile
    lateinit var dirDirFile: VirtualFile
    edtWriteAction {
      dir = rootDir.createChildDirectory(this, "dir")
      dirDir = dir.createChildDirectory(this, "dir_dir")
      dirDirFile = dirDir.createChildData(this, "file.txt")
      PsiTestUtil.addContentRoot(module, dir)
      PsiTestUtil.addExcludedRoot(module, dirDir)
    }
    readAction {
      assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(psiManager.findDirectory(dir)))
      assertEquals("dir_dir", CopyReferenceAction.elementToFqn(psiManager.findDirectory(dirDir)))
      assertEquals("dir_dir/file.txt", CopyReferenceAction.elementToFqn(psiManager.findFile(dirDirFile)))
    }
  }
}

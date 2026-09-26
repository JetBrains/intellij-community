// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.files

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.workspaceModel.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestApplication
class JavaSourceFilesFunctionsTest {
  @RegisterExtension
  @JvmField
  val projectModel = ProjectModelExtension()
  
  private val moduleEntity: ModuleEntity
    get() = projectModel.project.workspaceModel.currentSnapshot.resolve(ModuleId(module.name))!!

  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `find file by path from source root`() = runBlocking {
    val (aFile, bFile) = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
      val aFile = srcRoot.createFile("a.txt")
      val bFile = srcRoot.createFile("b/c.txt")
      aFile to bFile
    }
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(bFile, moduleEntity.findResourceFileByRelativePath("b/c.txt"))
    assertNull(moduleEntity.findResourceFileByRelativePath("c.txt"))
  }
  
  @Test
  fun `find file by path from source root with package prefix`() = runBlocking {
    val aFile = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
      srcRoot.createFile("a.txt")
    }
    projectModel.project.workspaceModel.update { storage ->
      val entity = storage.entities(JavaSourceRootPropertiesEntity::class.java).first()
      storage.modifyEntity(JavaSourceRootPropertiesEntity.Builder::class.java, entity) {
        packagePrefix = "foo"
      }
    }
    assertNull(moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("foo/a.txt"))
  }
  
  @Test
  fun `find file by path from resource root with prefix`() = runBlocking {
    val aFile = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaResourceRootType.RESOURCE)
      srcRoot.createFile("a.txt")
    }
    projectModel.project.workspaceModel.update { storage ->
      val entity = storage.entities(JavaResourceRootPropertiesEntity::class.java).first()
      storage.modifyEntity(JavaResourceRootPropertiesEntity.Builder::class.java, entity) {
        relativeOutputPath = "foo"
      }
    }
    assertNull(moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("foo/a.txt"))
  }
}
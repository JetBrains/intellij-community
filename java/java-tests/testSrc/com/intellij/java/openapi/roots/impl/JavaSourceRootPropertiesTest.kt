// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.roots.impl

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class JavaSourceRootPropertiesTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  lateinit var module: Module

  @Before
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `add root with package prefix`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("content/src")
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(contentRoot).addSourceFolder(srcRoot, false, "foo")
    }
    val committed = ModuleRootManager.getInstance(module)
    val committedSource = committed.contentEntries.single().sourceFolders.single()
    assertThat(committedSource.packagePrefix).isEqualTo("foo")
  }

  @Test
  fun `change both packagePrefix and forGeneratedSources properties`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("content/src")
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(contentRoot).addSourceFolder(srcRoot, false)
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val sourceFolder = model.contentEntries.single().sourceFolders.single()
      sourceFolder.packagePrefix = "foo"
      sourceFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)!!.isForGeneratedSources = true
    }
    val committed = ModuleRootManager.getInstance(module)
    val committedSource = committed.contentEntries.single().sourceFolders.single()
    assertThat(committedSource.packagePrefix).isEqualTo("foo")
    assertThat(committedSource.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)!!.isForGeneratedSources).isTrue()
  }

  @Test
  fun `load properties from entity`() {
    runBlocking {
      val contentDir = projectModel.baseProjectDir.newVirtualDirectory("content")
      val srcDir = projectModel.baseProjectDir.newVirtualDirectory("content/src")
      val resourcesDir = projectModel.baseProjectDir.newVirtualDirectory("content/resources")
      val workspaceModel = projectModel.project.workspaceModel
      workspaceModel.update { storage ->
        val moduleEntity = module.findModuleEntity()!!
        storage.modifyModuleEntity(moduleEntity) {
          val contentRootEntity = ContentRootEntity(
            url = contentDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()),
            excludedPatterns = emptyList(),
            entitySource = NonPersistentEntitySource,
          )
          val sourceRootEntity = SourceRootEntity(
            url = srcDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()),
            rootTypeId = JAVA_SOURCE_ROOT_ENTITY_TYPE_ID,
            entitySource = NonPersistentEntitySource,
          )
          contentRootEntity.sourceRoots += sourceRootEntity
          sourceRootEntity.javaSourceRoots += JavaSourceRootPropertiesEntity(
            generated = true,
            packagePrefix = "foo",
            entitySource = NonPersistentEntitySource,
          )
          val resourceRootEntity = SourceRootEntity(
            url = resourcesDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()),
            rootTypeId = JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID,
            entitySource = NonPersistentEntitySource,
          )
          contentRootEntity.sourceRoots += resourceRootEntity
          resourceRootEntity.javaResourceRoots += JavaResourceRootPropertiesEntity(
            generated = true,
            relativeOutputPath = "bar",
            entitySource = NonPersistentEntitySource,
          )
          contentRoots += contentRootEntity
        }
      }
      val contentFolder = ModuleRootManager.getInstance(module).contentEntries.single()
      assertThat(contentFolder.file).isEqualTo(contentDir)
      val sourceRootFolder = contentFolder.getSourceFolders(JavaModuleSourceRootTypes.SOURCES).single()
      assertThat(sourceRootFolder.file).isEqualTo(srcDir)
      assertThat(sourceRootFolder.packagePrefix).isEqualTo("foo")
      assertThat(sourceRootFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)!!.isForGeneratedSources).isTrue()

      val resourceRootFolder = contentFolder.getSourceFolders(JavaModuleSourceRootTypes.RESOURCES).single()
      assertThat(resourceRootFolder.file).isEqualTo(resourcesDir)
      assertThat(resourceRootFolder.packagePrefix).isEqualTo("bar")
      assertThat(resourceRootFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.RESOURCES)!!.isForGeneratedSources).isTrue()
    }
  }

  @Test
  fun `change root type`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("content/src")
    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntry = model.addContentEntry(contentRoot)
      val sourceFolder1 = contentEntry.addSourceFolder(srcRoot, false)
      assertThat(sourceFolder1.jpsElement.properties).isInstanceOf(JavaSourceRootProperties::class.java)
      contentEntry.removeSourceFolder(sourceFolder1)
      val sourceFolder2 = contentEntry.addSourceFolder(srcRoot, JavaResourceRootType.RESOURCE)
      assertThat(sourceFolder2.jpsElement.properties).isInstanceOf(JavaResourceRootProperties::class.java)
    }
    val folder = ModuleRootManager.getInstance(module).contentEntries.single().sourceFolders.single()
    assertThat(folder.jpsElement.properties).isInstanceOf(JavaResourceRootProperties::class.java)
  }

  @Test
  fun `add source folder for a bare java source root returns the existing root`() {
    checkBareRootIsReused(JAVA_SOURCE_ROOT_ENTITY_TYPE_ID) { entry, dir -> entry.addSourceFolder(dir, false) }
  }

  @Test
  fun `add test source folder for a bare java test root returns the existing root`() {
    checkBareRootIsReused(JAVA_TEST_ROOT_ENTITY_TYPE_ID) { entry, dir -> entry.addSourceFolder(dir, true) }
  }

  @Test
  fun `add resource folder for a bare java resource root returns the existing root`() {
    checkBareRootIsReused(JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID) { entry, dir -> entry.addSourceFolder(dir, JavaResourceRootType.RESOURCE) }
  }

  /**
   * Writes a [SourceRootEntity] of [rootTypeId] with no properties child.
   * Then it marks the same directory with [addSourceFolder] and expects one root everywhere.
   */
  private fun checkBareRootIsReused(rootTypeId: SourceRootTypeId, addSourceFolder: (ContentEntry, VirtualFile) -> SourceFolder) {
    val contentDir = projectModel.baseProjectDir.newVirtualDirectory("content")
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("content/src")
    val workspaceModel = projectModel.project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    runBlocking {
      workspaceModel.update { storage ->
        storage.modifyModuleEntity(module.findModuleEntity()!!) {
          val contentRootEntity = ContentRootEntity(
            url = contentDir.toVirtualFileUrl(urlManager),
            excludedPatterns = emptyList(),
            entitySource = NonPersistentEntitySource,
          )
          contentRootEntity.sourceRoots += SourceRootEntity(
            url = srcDir.toVirtualFileUrl(urlManager),
            rootTypeId = rootTypeId,
            entitySource = NonPersistentEntitySource,
          )
          contentRoots += contentRootEntity
        }
      }
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntry = model.contentEntries.single()
      val existing = contentEntry.sourceFolders.single()
      val returned = addSourceFolder(contentEntry, srcDir)
      assertThat(returned).isEqualTo(existing)
      assertThat(contentEntry.sourceFolders).hasSize(1)
    }

    val committed = ModuleRootManager.getInstance(module).contentEntries.single().sourceFolders
    assertThat(committed).hasSize(1)
    assertThat(committed.single().url).isEqualTo(srcDir.url)

    val sourceRoots = module.findModuleEntity()!!.sourceRoots.filter { it.url.url == srcDir.url }
    assertThat(sourceRoots).hasSize(1)
    assertThat(sourceRoots.single().rootTypeId).isEqualTo(rootTypeId)
  }
}
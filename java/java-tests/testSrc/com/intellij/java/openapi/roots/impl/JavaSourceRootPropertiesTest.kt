// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
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
}
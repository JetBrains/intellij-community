// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
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

}
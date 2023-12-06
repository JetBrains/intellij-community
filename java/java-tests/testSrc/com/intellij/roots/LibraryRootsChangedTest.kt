// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.zipFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Checks that proper [com.intellij.openapi.roots.ModuleRootListener.rootsChanged] are sent for changes in library roots. 
 */
class LibraryRootsChangedTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private lateinit var moduleRootListener: RootsChangedTest.MyModuleRootListener
  private lateinit var module: Module

  @Before
  fun setUp() {
    moduleRootListener = RootsChangedTest.MyModuleRootListener(projectModel.project)
    projectModel.project.messageBus.connect(disposableRule.disposable).subscribe(ModuleRootListener.TOPIC, moduleRootListener)
    module = projectModel.createModule("main")
  }

  @Test
  fun `create library JAR`() {
    createLibraryJar("a.jar")
  }

  @Test
  fun `create library JAR in subdirectory`() {
    createLibraryJar("lib/a.jar")
  }

  private fun createLibraryJar(relativePath: String) {
    val jarPath = projectModel.baseProjectDir.rootPath.resolve(relativePath)
    val jarFile = jarPath.toFile()
    val url = VfsUtil.getUrlForLibraryRoot(jarFile)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(url, OrderRootType.CLASSES)
    }
    moduleRootListener.reset()

    zipFile {
      file("a.txt")
    }.generate(jarFile)

    val manager = VirtualFileManager.getInstance()
    manager.refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(jarFile.path))
    val file = manager.refreshAndFindFileByUrl(url)
    assertThat(file).isNotNull
    moduleRootListener.assertEventsCountAndIncrementModificationCount(1, true, false)
  }

  @Test
  fun `create library directory with jar extension`() {
    createLibraryDirectory("dir.jar")
  }

  @Test
  fun `create library directory`() {
    createLibraryDirectory("dir")
  }

  @Test
  fun `create library directory in subdirectory`() {
    createLibraryDirectory("lib/dir")
  }

  private fun createLibraryDirectory(relativePath: String) {
    val path = projectModel.baseProjectDir.rootPath.resolve(relativePath)
    val url = VfsUtil.pathToUrl(path.systemIndependentPath)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(url, OrderRootType.CLASSES)
    }
    moduleRootListener.reset()

    path.createDirectories()
    val file = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url)
    assertThat(file).isNotNull
    moduleRootListener.assertEventsCountAndIncrementModificationCount(1, true, false)
  }
}
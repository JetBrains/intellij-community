/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase

/**
 * Dependencies: 'main' -> 'm2', 'main' -> 'm4', 'main' -> 'm5', 'main' -> 'm6' => 'm7'
 */
object MultiModuleJava9ProjectDescriptor : DefaultLightProjectDescriptor() {
  enum class ModuleDescriptor(internal val moduleName: String, internal val rootName: String) {
    MAIN(TEST_MODULE_NAME, "/not_used/"),
    M2("${TEST_MODULE_NAME}_m2", "src_m2"),
    M3("${TEST_MODULE_NAME}_m3", "src_m3"),
    M4("${TEST_MODULE_NAME}_m4", "src_m4"),
    M5("${TEST_MODULE_NAME}_m5", "src_m5"),
    M6("${TEST_MODULE_NAME}_m6", "src_m6"),
    M7("${TEST_MODULE_NAME}_m7", "src_m7");

    fun root(): VirtualFile =
        if (this == MAIN) LightPlatformTestCase.getSourceRoot() else TempFileSystem.getInstance().findFileByPath("/$rootName")!!
  }

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)
    runWriteAction {
      val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!

      val m2 = makeModule(project, ModuleDescriptor.M2)
      ModuleRootModificationUtil.addDependency(main, m2)

      makeModule(project, ModuleDescriptor.M3)

      val m4 = makeModule(project, ModuleDescriptor.M4)
      ModuleRootModificationUtil.addDependency(main, m4)

      val m5 = makeModule(project, ModuleDescriptor.M5)
      ModuleRootModificationUtil.addDependency(main, m5)

      val m6 = makeModule(project, ModuleDescriptor.M6)
      ModuleRootModificationUtil.addDependency(main, m6)

      val m7 = makeModule(project, ModuleDescriptor.M7)
      ModuleRootModificationUtil.addDependency(m6, m7, DependencyScope.COMPILE, true)
    }
  }

  private fun makeModule(project: Project, descriptor: ModuleDescriptor): Module {
    val path = FileUtil.join(FileUtil.getTempDirectory(), "${descriptor.moduleName}.iml")
    val module = createModule(project, path)
    val sourceRoot = createSourceRoot(module, descriptor.rootName)
    createContentEntry(module, sourceRoot)
    return module
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_9
  }

  fun cleanupSourceRoots() = runWriteAction {
    ModuleDescriptor.values().asSequence()
        .filter { it != ModuleDescriptor.MAIN }
        .flatMap { it.root().children.asSequence() }
        .forEach { it.delete(this) }
  }
}
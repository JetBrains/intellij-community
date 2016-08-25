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
package com.intellij.testFramework

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor

object MultiModuleJava9ProjectDescriptor  : DefaultLightProjectDescriptor() {
  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)
    runWriteAction {
      val m2 = createModule(project, FileUtil.join(FileUtil.getTempDirectory(), "light_idea_test_case_m2.iml"))
      val src2 = createSourceRoot(m2, "src2")
      createContentEntry(m2, src2)

      VfsUtil.saveText(src2.createChildData(this, "module-info.java"), "module M2 { requires M1; }")
    }
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_9
  }
}
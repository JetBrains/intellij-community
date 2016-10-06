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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN

abstract class LightJava9ModulesCodeInsightFixtureTestCase : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  override fun tearDown() {
    MultiModuleJava9ProjectDescriptor.cleanupSourceRoots()
    super.tearDown()
  }

  protected fun addFile(path: String, text: String, module: ModuleDescriptor = MAIN): VirtualFile =
      VfsTestUtil.createFile(module.root(), path, text)
}
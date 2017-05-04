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
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.createModule
import com.intellij.configurationStore.useAndDispose
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.parentSystemIndependentPath
import com.intellij.util.io.readText
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@RunsInEdt
@RunsInActiveStoreMode
class ExternalSystemStorageTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  @Suppress("unused")
  @JvmField
  @Rule
  val ruleChain = RuleChain(tempDirManager, EdtRule(), ActiveStoreRule(projectRule), DisposeModulesRule(projectRule), ExternalStorageRule())

  @Test
  fun `must be empty if external system storage`() {
    // we must not use VFS here, file must not be created
    val moduleFile = tempDirManager.newPath("module", refreshVfs = true).resolve("test.iml")
    projectRule.createModule(moduleFile).useAndDispose {
      setOption(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY, "true")

      ModuleRootModificationUtil.addContentRoot(this, moduleFile.parentSystemIndependentPath)
      saveStore()
      assertThat(moduleFile).isRegularFile
      assertThat(moduleFile.readText()).startsWith("""
      <?xml version="1.0" encoding="UTF-8"?>
      <module org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule="true" type="JAVA_MODULE" version="4" />""".trimIndent())
    }
  }
}

private class ExternalStorageRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return statement {
      try {
        IS_ENABLED = true
        base.evaluate()
      }
      finally {
        IS_ENABLED = false
      }
    }
  }
}
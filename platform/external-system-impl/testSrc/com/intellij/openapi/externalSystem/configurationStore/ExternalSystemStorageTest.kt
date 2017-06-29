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

import com.intellij.configurationStore.ESCAPED_MODULE_DIR
import com.intellij.configurationStore.createModule
import com.intellij.configurationStore.useAndDispose
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.delete
import com.intellij.util.io.parentSystemIndependentPath
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
  val ruleChain = RuleChain(tempDirManager, EdtRule(), ActiveStoreRule(projectRule), DisposeModulesRule(projectRule), ExternalStorageRule(projectRule.project))

  @Test
  fun `must be empty if external system storage`() {
    val cacheDir = ExternalProjectsDataStorage.getProjectConfigurationDir(projectRule.project).resolve("modules")
    cacheDir.delete()

    // we must not use VFS here, file must not be created
    val moduleFile = tempDirManager.newPath("module", refreshVfs = true).resolve("test.iml")
    projectRule.createModule(moduleFile).useAndDispose {
      assertThat(cacheDir).doesNotExist()

      ModuleRootModificationUtil.addContentRoot(this, moduleFile.parentSystemIndependentPath)

      saveStore()
      assertThat(cacheDir).doesNotExist()
      assertThat(moduleFile).isEqualTo("""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://$ESCAPED_MODULE_DIR" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>""")

      ExternalSystemModulePropertyManager.getInstance(this).setMavenized(true)
      // force re-save: this call not in the setMavenized because ExternalSystemModulePropertyManager in the API (since in production we have the only usage, it is ok for now)
      (ModuleRootManager.getInstance(this) as ModuleRootManagerImpl).stateChanged()

      assertThat(cacheDir).doesNotExist()
      saveStore()
      assertThat(cacheDir).isDirectory
      assertThat(moduleFile).isEqualTo("""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4" />""")

      assertThat(cacheDir.resolve("test.xml")).isEqualTo("""
      <module>
        <component name="ExternalSystem" externalSystem="Maven" />
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://$ESCAPED_MODULE_DIR" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>""")
    }
  }
}

private class ExternalStorageRule(private val project: Project) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return statement {
      val manager = ExternalProjectsManagerImpl.getInstance(project)
      try {
        manager.setStoreExternally(true)
        base.evaluate()
      }
      finally {
        manager.setStoreExternally(false)
      }
    }
  }
}
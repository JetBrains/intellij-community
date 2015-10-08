package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

internal class DefaultProjectStoreTest {
  companion object {
    @ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  private val requiredPlugins = listOf<ProjectExternalDependency>(DependencyOnPlugin("fake", "0", "1"))

  private val ruleChain = RuleChain(
    tempDirManager,
    WrapRule {
      val app = ApplicationManagerEx.getApplicationEx()
      val isDoNotSave = app.isDoNotSave
      app.doNotSave(false);
      {
        try {
          app.doNotSave(isDoNotSave)
        }
        finally {
          Paths.get(app.stateStore.stateStorageManager.expandMacros(StoragePathMacros.APP_CONFIG)).deleteRecursively()
        }
      }
    },
    WrapRule {
      val externalDependenciesManager = ProjectManager.getInstance().defaultProject.service<ExternalDependenciesManager>()
      externalDependenciesManager.allDependencies = requiredPlugins
      {
        externalDependenciesManager.allDependencies = emptyList()
      }
    }
  )

  @Rule fun getChain() = ruleChain

  @Test fun `new project from default`() {
    createProjectAndUseInLoadComponentStateMode(tempDirManager) {
      assertThat(it.service<ExternalDependenciesManager>().allDependencies).isEqualTo(requiredPlugins)
    }
  }
}
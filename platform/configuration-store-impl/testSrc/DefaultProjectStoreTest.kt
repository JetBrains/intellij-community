package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.deleteRecursively
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.nio.file.Paths

internal class DefaultProjectStoreTest {
  companion object {
    @ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  private val requiredPlugins = listOf<ProjectExternalDependency>(DependencyOnPlugin("fake", "0", "1"))

  private val ruleChain = RuleChain(
    tempDirManager,
    object : ExternalResource() {
      private var isDoNotSave = false

      override fun before() {
        val app = ApplicationManagerEx.getApplicationEx()
        isDoNotSave = app.isDoNotSave
        app.doNotSave(false)
      }

      override fun after() {
        val app = ApplicationManagerEx.getApplicationEx()
        try {
          app.doNotSave(isDoNotSave)
        }
        finally {
          Paths.get(app.stateStore.stateStorageManager.expandMacros(StoragePathMacros.APP_CONFIG)).deleteRecursively()
        }
      }
    },
    object : ExternalResource() {
      private var externalDependenciesManager: ExternalDependenciesManager? = null

      override fun before() {
        val defaultProject = ProjectManager.getInstance().defaultProject
        externalDependenciesManager = defaultProject.service<ExternalDependenciesManager>()
        externalDependenciesManager!!.allDependencies = requiredPlugins
      }

      override fun after() {
        externalDependenciesManager?.allDependencies = emptyList()
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
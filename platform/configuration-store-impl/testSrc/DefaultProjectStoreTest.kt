package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class DefaultProjectStoreTest {
  companion object {
    ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  private val requiredPlugins: List<ProjectExternalDependency> = listOf(DependencyOnPlugin("fake", "0", "1"))

  private val ruleChain = RuleChain(
    tempDirManager,
    object : ExternalResource() {
      private var isDoNotSave = false

      override fun before() {
        val app = ApplicationManagerEx.getApplicationEx()
        isDoNotSave = app.isDoNotSave()
        app.doNotSave(false)
      }

      override fun after() {
        val app = ApplicationManagerEx.getApplicationEx()
        try {
          app.doNotSave(isDoNotSave)
        }
        finally {
          FileUtil.delete(File(app.stateStore.getStateStorageManager().expandMacros(StoragePathMacros.APP_CONFIG)))
        }
      }
    },
    object : ExternalResource() {
      private var externalDependenciesManager: ExternalDependenciesManager? = null

      override fun before() {
        externalDependenciesManager = ProjectManager.getInstance().getDefaultProject().service<ExternalDependenciesManager>()
        externalDependenciesManager!!.setAllDependencies(requiredPlugins)
      }

      override fun after() {
        externalDependenciesManager?.setAllDependencies(emptyList())
      }
    }
  )

  public Rule fun getChain(): RuleChain = ruleChain

  public Test fun `new project from default`() {
    createProject(tempDirManager) {
      assertThat(it.service<ExternalDependenciesManager>().getAllDependencies()).isEqualTo(requiredPlugins)
    }
  }
}
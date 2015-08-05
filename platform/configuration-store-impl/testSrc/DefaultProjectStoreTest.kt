package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class DefaultProjectStoreTest {
  private val fixtureManager = FixtureRule()
  private val tempDirManager = TemporaryDirectory()

  private val requiredPlugins: List<ProjectExternalDependency> = listOf(DependencyOnPlugin("fake", "0", "1"))

  private val ruleChain = RuleChain(
    tempDirManager,
    fixtureManager,
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
        val externalDependenciesManager = ProjectManager.getInstance().getDefaultProject().service<ExternalDependenciesManager>()
        externalDependenciesManager.setAllDependencies(requiredPlugins)
      }

      override fun after() {
        externalDependenciesManager?.setAllDependencies(emptyList())
      }
    }
  )

  public Rule fun getChain(): RuleChain = ruleChain

  public Test fun `new project from default`() {
    invokeAndWaitIfNeed {
      val project = (ProjectManager.getInstance() as ProjectManagerImpl).newProject("test", tempDirManager.newDirectory().systemIndependentPath, true, false, false)!!
      try {
        assertThat(project.service<ExternalDependenciesManager>().getAllDependencies(), equalTo(requiredPlugins))
      }
      finally {
        runWriteAction { Disposer.dispose(project) }
      }
    }
  }
}
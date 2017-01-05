package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.testFramework.*
import com.intellij.util.io.delete
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.isEmpty
import com.intellij.util.loadElement
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

internal class DefaultProjectStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

    internal const val TEST_COMPONENT_NAME = "Foo"

    @State(name = TEST_COMPONENT_NAME, storages = arrayOf(Storage(value = "testSchemes", stateSplitter = TestStateSplitter::class)))
    private class TestComponent: PersistentStateComponent<Element> {
      private var element = Element("state")

      override fun getState() = element.clone()

      override fun loadState(state: Element) {
        element = state.clone()
      }
    }
  }

  private val tempDirManager = TemporaryDirectory()

  private val requiredPlugins = listOf<ProjectExternalDependency>(DependencyOnPlugin("fake", "0", "1", "alpha"))

  private val ruleChain = RuleChain(
    tempDirManager,
    WrapRule {
      val app = ApplicationManagerEx.getApplicationEx()
      val path = Paths.get(app.stateStore.stateStorageManager.expandMacros(APP_CONFIG))
      // dream about using in memory fs per test as ICS partially does and avoid such hacks
      path.refreshVfs()

      val isDoNotSave = app.isDoNotSave
      app.doNotSave(false);
      {
        try {
          app.doNotSave(isDoNotSave)
        }
        finally {
          path.delete()
          val virtualFile = LocalFileSystem.getInstance().findFileByPathIfCached(path.systemIndependentPath)
          runInEdtAndWait { runWriteAction { virtualFile?.delete(null) } }
        }
      }
    }
  )

  @Rule fun getChain() = ruleChain

  @Test fun `new project from default - file-based storage`() {
    val externalDependenciesManager = ProjectManager.getInstance().defaultProject.service<ExternalDependenciesManager>()
    externalDependenciesManager.allDependencies = requiredPlugins
    try {
      createProjectAndUseInLoadComponentStateMode(tempDirManager) {
        assertThat(it.service<ExternalDependenciesManager>().allDependencies).isEqualTo(requiredPlugins)
      }
    }
    finally {
      externalDependenciesManager.allDependencies = emptyList()
    }
  }

  @Test fun `new project from default - directory-based storage`() {
    val defaultProject = ProjectManager.getInstance().defaultProject
    val defaultTestComponent = TestComponent()
    defaultTestComponent.loadState(loadElement("""<component><main name="$TEST_COMPONENT_NAME"/><sub name="foo" /><sub name="bar" /></component>"""))
    val stateStore = defaultProject.stateStore as ComponentStoreImpl
    stateStore.initComponent(defaultTestComponent, true)
    try {
      // obviously, project must be directory-based also
      createProjectAndUseInLoadComponentStateMode(tempDirManager, directoryBased = true) {
        val component = TestComponent()
        it.stateStore.initComponent(component, true)
        assertThat(JDOMUtil.writeElement(component.state)).isEqualTo(JDOMUtil.writeElement(defaultTestComponent.state))
      }
    }
    finally {
      stateStore.removeComponent(TEST_COMPONENT_NAME)
    }
  }

  @Test fun `new project from default - remove workspace component configuration`() {
    val element = loadElement("""
    <state>
      <component name="ProjectLevelVcsManager" settingsEditedManually="false" />
    </state>""")
    removeWorkspaceComponentConfiguration(ProjectManager.getInstance().defaultProject, element)
    assertThat(element.isEmpty()).isTrue()
  }
}
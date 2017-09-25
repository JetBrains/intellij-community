package com.intellij.configurationStore

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.externalDependencies.ProjectExternalDependency
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.delete
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.isEmpty
import com.intellij.util.loadElement
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

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

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
        assertThat(component.state).isEqualTo(defaultTestComponent.state)
      }
    }
    finally {
      stateStore.removeComponent(TEST_COMPONENT_NAME)
    }
  }

  @Test fun `new project from default - remove workspace component configuration`() {
    val element = loadElement("""
    <state>
      <component name="CopyrightManager">
        <copyright>
          <option name="myName" value="Foo" />
          <option name="notice" value="where" />
        </copyright>
      </component>
      <component name="GreclipseSettings">
        <option name="greclipsePath" value="foo"/>
      </component>
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="AntDuplicateTargetsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
          <inspection_tool class="AntMissingPropertiesFileInspection" enabled="false" level="ERROR" enabled_by_default="false" />
          <inspection_tool class="AntResolveInspection" enabled="false" level="ERROR" enabled_by_default="false" />
          <inspection_tool class="ArgNamesErrorsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
          <inspection_tool class="ArgNamesWarningsInspection" enabled="false" level="WARNING" enabled_by_default="false" />
          <inspection_tool class="AroundAdviceStyleInspection" enabled="false" level="WARNING" enabled_by_default="false" />
          <inspection_tool class="DeclareParentsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
          <inspection_tool class="EmptyEventHandler" enabled="false" level="WARNING" enabled_by_default="false" />
          <inspection_tool class="PointcutMethodStyleInspection" enabled="false" level="WARNING" enabled_by_default="false" />
        </profile>
        <version value="1.0" />
      </component>
      <component name="ProjectLevelVcsManager" settingsEditedManually="false" />
      <component name="masterDetails">
        <states>
          <state key="Copyright.UI">
            <settings>
              <last-edited>Foo</last-edited>
              <splitter-proportions>
                <option name="proportions">
                  <list>
                    <option value="0.2" />
                  </list>
                </option>
              </splitter-proportions>
            </settings>
          </state>
          <state key="ProjectJDKs.UI">
            <settings>
              <last-edited>1.4</last-edited>
              <splitter-proportions>
                <option name="proportions">
                  <list>
                    <option value="0.2" />
                  </list>
                </option>
              </splitter-proportions>
            </settings>
          </state>
        </states>
      </component>
      <component name="ProjectModuleManager">
        <modules>
          <module fileurl="file://USER_HOME$/PycharmProjects/ttt002/.idea/ttt002.iml" filepath="USER_HOME$/PycharmProjects/ttt002/.idea/ttt002.iml" />
        </modules>
      </component>
      <component name="PropertiesComponent">
        <property name="settings.editor.selected.configurable" value="preferences.lookFeel" />
        <property name="settings.editor.splitter.proportion" value="0.2" />
      </component>
    </state>""")

    val tempDir = fsRule.fs.getPath("")
    normalizeDefaultProjectElement(ProjectManager.getInstance().defaultProject, element, tempDir)
    assertThat(element.isEmpty()).isTrue()

    val directoryTree = printDirectoryTree(tempDir)
    assertThat(directoryTree.trim()).isEqualTo("""
    ├──/
      ├──compiler.xml
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="GreclipseSettings">
        <option name="greclipsePath" value="foo" />
      </component>
    </project>

      ├──copyright/
        ├──Foo.xml
    <component name="CopyrightManager">
      <copyright>
        <option name="myName" value="Foo" />
        <option name="notice" value="where" />
      </copyright>
    </component>

      ├──inspectionProfiles/
        ├──Project_Default.xml
    <component name="InspectionProjectProfileManager">
      <profile version="1.0">
        <option name="myName" value="Project Default" />
        <inspection_tool class="AntDuplicateTargetsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
        <inspection_tool class="AntMissingPropertiesFileInspection" enabled="false" level="ERROR" enabled_by_default="false" />
        <inspection_tool class="AntResolveInspection" enabled="false" level="ERROR" enabled_by_default="false" />
        <inspection_tool class="ArgNamesErrorsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
        <inspection_tool class="ArgNamesWarningsInspection" enabled="false" level="WARNING" enabled_by_default="false" />
        <inspection_tool class="AroundAdviceStyleInspection" enabled="false" level="WARNING" enabled_by_default="false" />
        <inspection_tool class="DeclareParentsInspection" enabled="false" level="ERROR" enabled_by_default="false" />
        <inspection_tool class="EmptyEventHandler" enabled="false" level="WARNING" enabled_by_default="false" />
        <inspection_tool class="PointcutMethodStyleInspection" enabled="false" level="WARNING" enabled_by_default="false" />
      </profile>
    </component>

      ├──workspace.xml
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="ProjectLevelVcsManager" settingsEditedManually="false" />
      <component name="masterDetails">
        <states>
          <state key="Copyright.UI">
            <settings>
              <last-edited>Foo</last-edited>
              <splitter-proportions>
                <option name="proportions">
                  <list>
                    <option value="0.2" />
                  </list>
                </option>
              </splitter-proportions>
            </settings>
          </state>
          <state key="ProjectJDKs.UI">
            <settings>
              <last-edited>1.4</last-edited>
              <splitter-proportions>
                <option name="proportions">
                  <list>
                    <option value="0.2" />
                  </list>
                </option>
              </splitter-proportions>
            </settings>
          </state>
        </states>
      </component>
      <component name="PropertiesComponent">
        <property name="settings.editor.selected.configurable" value="preferences.lookFeel" />
        <property name="settings.editor.splitter.proportion" value="0.2" />
      </component>
    </project>""".trimIndent())
  }
}
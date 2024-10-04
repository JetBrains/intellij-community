// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.PathUtil
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.properties.Delegates

class ProjectStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Language("XML")
  private val iprFileContent = """
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="AATestComponent">
        <option name="AAValue" value="customValue" />
      </component>
    </project>""".trimIndent()

  @State(name = "AATestComponent", allowLoadInTests = true)
  private class TestComponent : PersistentStateComponent<TestState> {
    private var state: TestState? = null

    override fun getState() = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  @Suppress("PropertyName")
  private data class TestState(var AAValue: String = "default")

  @Test
  fun directoryBasedStorage() = runBlocking {
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      it.toNioPath()
    }) { project ->
      val testComponent = test(project as ProjectEx)

      assertThat(project.basePath).isEqualTo(PathUtil.getParentPath((PathUtil.getParentPath(project.projectFilePath!!))))

      // test reload on external change
      val file = project.stateStore.storageManager.expandMacro(PROJECT_FILE)
      file.writeText(file.readText().replace("""<option name="AAValue" value="foo" />""", """<option name="AAValue" value="newValue" />"""))

      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance(project).reloadChangedStorageFiles()

      assertThat(testComponent.state).isEqualTo(TestState("newValue"))

      testComponent.state!!.AAValue = "s".repeat(FileSizeLimit.getContentLoadLimit() + 1024)
      project.stateStore.save()

      // we should save twice (first call - virtual file size is not yet set)
      testComponent.state!!.AAValue = "b".repeat(FileSizeLimit.getContentLoadLimit() + 1024)
      project.stateStore.save()
    }
  }

  @Test
  fun fileBasedStorage() = runBlocking {
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      it.writeChild("test${ProjectFileType.DOT_DEFAULT_EXTENSION}", iprFileContent).toNioPath()
    }) { project ->
      test(project)

      assertThat(project.basePath).isEqualTo(PathUtil.getParentPath(project.projectFilePath!!))
    }
  }

  @Test
  fun saveProjectName() = runBlocking {
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      // test BOM
      val out = ByteArrayOutputStream()
      out.write(0xef)
      out.write(0xbb)
      out.write(0xbf)
      out.write(iprFileContent.toByteArray())
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", out.toByteArray())
      it.toNioPath()
    }) { project ->
      val store = project.stateStore as ProjectStoreImpl
      assertThat(store.getNameFile()).doesNotExist()
      val newName = "Foo"
      val oldName = project.name
      (project as ProjectEx).setProjectName(newName)
      project.stateStore.save()
      assertThat(store.getNameFile()).hasContent(newName)

      project.setProjectName("clear-read-only")
      File(store.getNameFile().toUri()).setReadOnly()

      val handler = ReadonlyStatusHandler.getInstance(project) as ReadonlyStatusHandlerImpl
      try {
        handler.setClearReadOnlyInTests(true)
        project.stateStore.save()
      }
      finally {
        handler.setClearReadOnlyInTests(false)
      }
      assertThat(store.getNameFile()).hasContent("clear-read-only")

      project.setProjectName(oldName)
      project.stateStore.save()
      assertThat(store.getNameFile()).doesNotExist()
    }
  }

  @Test
  fun `saved project name must be not removed just on open`() = runBlocking {
    var name: String by Delegates.notNull()
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      name = it.name
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/.name", name)
      it.toNioPath()
    }) { project ->
      val store = project.stateStore as ProjectStoreImpl
      assertThat(store.getNameFile()).hasContent(name)

      project.stateStore.save()
      assertThat(store.getNameFile()).hasContent(name)

      (project as ProjectEx).setProjectName(name)
      project.stateStore.save()
      assertThat(store.getNameFile()).hasContent(name)

      project.setProjectName("foo")
      project.stateStore.save()
      assertThat(store.getNameFile()).hasContent("foo")

      project.setProjectName(name)
      project.stateStore.save()
      assertThat(store.getNameFile()).doesNotExist()

      project.setProjectName("<html><img src=http:ip:port/attack.png> </html>")
      project.stateStore.save()
      assertThat(store.getNameFile()).doesNotExist()

      project.setProjectName("a < b or b > c")
      project.stateStore.save()
      assertThat(store.getNameFile()).hasContent("a b or b > c")
    }
  }

  @Test
  fun `remove stalled data`() = runBlocking {
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      @Language("XML")
      val expected = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ValidComponent" foo="some data" />
  <component name="AppLevelLoser" foo="old?" />
  <component name="ProjectLevelLoser" foo="old?" />
</project>""".trimIndent()
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/foo.xml", expected)
      it.toNioPath()
    }) { project ->
      val obsoleteStorageBean = ObsoleteStorageBean()
      val storageFileName = "foo.xml"
      obsoleteStorageBean.file = storageFileName
      obsoleteStorageBean.components.addAll(listOf("AppLevelLoser"))

      val projectStalledStorageBean = ObsoleteStorageBean()
      projectStalledStorageBean.file = storageFileName
      projectStalledStorageBean.isProjectLevel = true
      projectStalledStorageBean.components.addAll(listOf("ProjectLevelLoser"))
      ExtensionTestUtil.maskExtensions(ObsoleteStorageBean.EP_NAME, listOf(obsoleteStorageBean, projectStalledStorageBean), project)

      val componentStore = project.stateStore

      @State(name = "ValidComponent", storages = [(Storage(value = "foo.xml"))])
      class AOther : A()

      val component = AOther()
      componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
      assertThat(component.options.foo).isEqualTo("some data")

      componentStore.save()

      @Language("XML")
      val expected = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="AppLevelLoser" foo="old?" />
  <component name="ValidComponent" foo="some data" />
</project>""".trimIndent()
      assertThat(project.stateStore.storageManager.expandMacro(PROJECT_CONFIG_DIR).resolve(obsoleteStorageBean.file)).isEqualTo(expected)
    }
  }

  // heavy test that uses ProjectManagerImpl directly to test (opposite to DefaultProjectStoreTest)
  @Test
  fun `just created project must inherit settings from the default project`() {
    val projectManager = ProjectManagerEx.getInstanceEx()

    val testComponent = TestComponent()
    testComponent.loadState(TestState(AAValue = "foo"))
    (projectManager.defaultProject as ComponentManager).stateStore.initComponent(component = testComponent,
                                                                                 serviceDescriptor = null,
                                                                                 pluginId = PluginManagerCore.CORE_ID)

    runBlocking {
      val newProjectPath = tempDirManager.newPath()
      val newProject = projectManager.openProjectAsync(newProjectPath, OpenProjectTask { isNewProject = true; isRefreshVfsNeeded = false })!!
      newProject.useProjectAsync {
        newProject.stateStore.save(forceSavingAllSettings = true)
        val miscXml = newProjectPath.resolve(".idea/misc.xml").readText()
        assertThat(miscXml).contains("AATestComponent")
        assertThat(miscXml).contains("""<option name="AAValue" value="foo" />""")
      }
    }
  }

  private suspend fun test(project: Project): TestComponent {
    val testComponent = TestComponent()
    project.stateStore.initComponent(testComponent, null, PluginManagerCore.CORE_ID)
    assertThat(testComponent.state).isEqualTo(TestState("customValue"))

    testComponent.state!!.AAValue = "foo"
    project.stateStore.save()

    val file = project.stateStore.storageManager.expandMacro(PROJECT_FILE)
    assertThat(file).isRegularFile
    // test exact string - xml prolog, line separators, indentation and so on must be exactly the same
    // todo get rid of default component states here
    assertThat(file.readText()).startsWith(iprFileContent.replace("customValue", "foo").replace("</project>", ""))

    return testComponent
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.system.OS
import com.intellij.util.xmlb.KotlinxSerializationBinding
import com.intellij.util.xmlb.SettingsInternalApi
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

private const val PROJECT_ID = "0ujtsYcgvSTl8PAuAdqWYSMnLOv"

class ForeignStateReaderTest {
  @TempDir
  lateinit var tempDir: Path

  private val configDir: Path
    get() = tempDir.resolve("config")

  private val dotIdea: Path
    get() = tempDir.resolve("project/.idea")

  private val workspaceFile: Path
    get() = dotIdea.resolve("workspace.xml")

  private fun roots(os: OS = OS.Linux): ForeignSettingsRoots {
    return ForeignSettingsRoots(configDir = configDir, dotIdea = dotIdea, os = os)
  }

  @Test
  fun `per-OS storage uses the folder of the foreign OS`() {
    val perOsFile = configDir.resolve("options/windows/perOs.xml")
    val commonFile = configDir.resolve("options/perOs.xml")
    writeComponent(commonFile, "PerOs", "common")

    assertThat(ForeignStateReader.candidateFiles(ForeignPerOsComponent::class.java, isProjectLevel = false, roots(OS.Windows))).containsExactly(perOsFile, commonFile)
    assertThat(ForeignStateReader.candidateFiles(ForeignPerOsComponent::class.java, isProjectLevel = false, roots(OS.macOS)))
      .containsExactly(configDir.resolve("options/mac/perOs.xml"), commonFile)
    assertThat(ForeignStateReader.readState(ForeignPerOsComponent::class.java, isProjectLevel = false, roots(OS.Windows))?.value).isEqualTo("common")

    writeComponent(perOsFile, "PerOs", "windows")
    assertThat(ForeignStateReader.readState(ForeignPerOsComponent::class.java, isProjectLevel = false, roots(OS.Windows))?.value).isEqualTo("windows")
  }

  @Test
  fun `deprecated storage is the last one`() {
    val oldFile = configDir.resolve("options/old.xml")
    val newFile = configDir.resolve("options/new.xml")
    writeComponent(oldFile, "Deprecated", "old")

    assertThat(ForeignStateReader.candidateFiles(DeprecatedStorageComponent::class.java, isProjectLevel = false, roots())).containsExactly(newFile, oldFile)
    assertThat(ForeignStateReader.readState(DeprecatedStorageComponent::class.java, isProjectLevel = false, roots())?.value).isEqualTo("old")

    // a file without the component does not stop the search
    writeComponent(newFile, "Other", "other")
    assertThat(ForeignStateReader.readState(DeprecatedStorageComponent::class.java, isProjectLevel = false, roots())?.value).isEqualTo("old")

    writeComponent(newFile, "Deprecated", "new")
    assertThat(ForeignStateReader.readState(DeprecatedStorageComponent::class.java, isProjectLevel = false, roots())?.value).isEqualTo("new")
  }

  @Test
  fun `application storage macros`() {
    assertThat(ForeignStateReader.candidateFiles(AppMacroComponent::class.java, isProjectLevel = false, roots()))
      .containsExactly(configDir.resolve("options/app.xml"), configDir.resolve("root.xml"))
  }

  @Test
  fun `project storage in the project directory`() {
    writeComponent(dotIdea.resolve("project.xml"), "ProjectDir", "project", rootName = "project")
    assertThat(ForeignStateReader.readState(ProjectDirComponent::class.java, isProjectLevel = true, roots())?.value).isEqualTo("project")

    assertThat(ForeignStateReader.candidateFiles(ProjectDirComponent::class.java, isProjectLevel = true, roots()))
      .containsExactly(dotIdea.resolve("project.xml"), dotIdea.resolve("macro.xml"))
  }

  @Test
  fun `workspace file storage`() {
    writeComponent(workspaceFile, "Workspace", "workspace", rootName = "project")
    assertThat(ForeignStateReader.readState(WorkspaceComponent::class.java, isProjectLevel = true, roots())?.value).isEqualTo("workspace")
  }

  @Test
  fun `product workspace file storage`() {
    assertThat(ForeignStateReader.candidateFiles(ProductWorkspaceComponent::class.java, isProjectLevel = true, roots())).isEmpty()

    workspaceFile.createParentDirectories().writeText("""<project version="4"><component name="ProjectId" id="../evil" /></project>""")
    assertThat(ForeignStateReader.candidateFiles(ProductWorkspaceComponent::class.java, isProjectLevel = true, roots())).isEmpty()

    workspaceFile.writeText("""<project version="4"><component name="ProjectId" id="$PROJECT_ID" /></project>""")
    val productWorkspaceFile = configDir.resolve("workspace/$PROJECT_ID.xml")
    assertThat(ForeignStateReader.candidateFiles(ProductWorkspaceComponent::class.java, isProjectLevel = true, roots())).containsExactly(productWorkspaceFile)

    writeComponent(productWorkspaceFile, "ProductWorkspace", "product", rootName = "project")
    assertThat(ForeignStateReader.readState(ProductWorkspaceComponent::class.java, isProjectLevel = true, roots())?.value).isEqualTo("product")
  }

  @OptIn(SettingsInternalApi::class)
  @Test
  fun `kotlinx serialization state`() {
    val element = KotlinxSerializationBinding(SerializableState::class.java).serialize(SerializableState(foo = "hello", bar = 42), filter = null)!!
    element.name = "component"
    element.setAttribute("name", "Serializable")
    val file = configDir.resolve("options/serializable.xml")
    file.createParentDirectories().writeText(JDOMUtil.write(Element("application").addContent(element)))

    assertThat(ForeignStateReader.readState(SerializableComponent::class.java, isProjectLevel = false, roots()))
      .isEqualTo(SerializableState(foo = "hello", bar = 42))
  }

  @Test
  fun `unsupported storages give no file`() {
    assertThat(ForeignStateReader.candidateFiles(UnsupportedAppComponent::class.java, isProjectLevel = false, roots())).isEmpty()
    assertThat(ForeignStateReader.candidateFiles(UnsupportedProjectComponent::class.java, isProjectLevel = true, roots())).isEmpty()
  }

  @Test
  fun `missing roots give no file`() {
    val noRoots = ForeignSettingsRoots(configDir = null, dotIdea = null, os = OS.Linux)
    assertThat(ForeignStateReader.readState(ForeignPerOsComponent::class.java, isProjectLevel = false, noRoots)).isNull()
    assertThat(ForeignStateReader.readState(ProjectDirComponent::class.java, isProjectLevel = true, noRoots)).isNull()
    assertThat(ForeignStateReader.readState(WorkspaceComponent::class.java, isProjectLevel = true, noRoots)).isNull()
    assertThat(ForeignStateReader.readState(ProductWorkspaceComponent::class.java, isProjectLevel = true, noRoots)).isNull()
  }

  @Test
  fun `missing, empty and malformed files hold no state`() {
    assertThat(ForeignStateReader.readState(WorkspaceComponent::class.java, isProjectLevel = true, roots())).isNull()

    workspaceFile.createParentDirectories().writeText("")
    assertThat(ForeignStateReader.readState(WorkspaceComponent::class.java, isProjectLevel = true, roots())).isNull()

    workspaceFile.writeText("<project><component")
    assertThat(ForeignStateReader.readState(WorkspaceComponent::class.java, isProjectLevel = true, roots())).isNull()
  }

  private fun writeComponent(file: Path, componentName: String, value: String, rootName: String = "application") {
    file.createParentDirectories().writeText(
      """
      <$rootName>
        <component name="$componentName">
          <option name="value" value="$value" />
        </component>
      </$rootName>
      """.trimIndent()
    )
  }
}

private class ForeignState {
  @JvmField
  var value: String = ""
}

@Serializable
private data class SerializableState(val foo: String = "", val bar: Int = 0)

private abstract class ForeignComponent<S : Any> : PersistentStateComponent<S> {
  override fun getState(): S = throw UnsupportedOperationException()

  override fun loadState(state: S): Unit = throw UnsupportedOperationException()
}

@State(name = "PerOs", storages = [Storage("perOs.xml", roamingType = RoamingType.PER_OS)])
private class ForeignPerOsComponent : ForeignComponent<ForeignState>()

@State(name = "Deprecated", storages = [Storage("old.xml", deprecated = true), Storage("new.xml")])
private class DeprecatedStorageComponent : ForeignComponent<ForeignState>()

@State(name = "AppMacro", storages = [Storage("\$APP_CONFIG\$/app.xml"), Storage("\$ROOT_CONFIG\$/root.xml")])
private class AppMacroComponent : ForeignComponent<ForeignState>()

@State(name = "ProjectDir", storages = [Storage("project.xml"), Storage("\$PROJECT_CONFIG_DIR\$/macro.xml")])
private class ProjectDirComponent : ForeignComponent<ForeignState>()

@State(name = "Workspace", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
private class WorkspaceComponent : ForeignComponent<ForeignState>()

@State(name = "ProductWorkspace", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
private class ProductWorkspaceComponent : ForeignComponent<ForeignState>()

@State(name = "Serializable", storages = [Storage("serializable.xml")])
private class SerializableComponent : ForeignComponent<SerializableState>()

@State(
  name = "UnsupportedApp",
  storages = [
    Storage(StoragePathMacros.CACHE_FILE),
    Storage("directory", stateSplitter = TestDirectorySplitter::class),
    Storage(storageClass = TestStateStorage::class),
  ],
)
private class UnsupportedAppComponent : ForeignComponent<ForeignState>()

@State(name = "UnsupportedProject", storages = [Storage(StoragePathMacros.CACHE_FILE), Storage(StoragePathMacros.PROJECT_FILE)])
private class UnsupportedProjectComponent : ForeignComponent<ForeignState>()

private abstract class TestStateStorage : StateStorage

private class TestDirectorySplitter : MainConfigurationStateSplitter() {
  override fun getComponentStateFileName(): String = "main"

  override fun getSubStateTagName(): String = "sub"
}

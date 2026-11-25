// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.createRunConfigurationActionGroup
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.rules.ProjectModelRule
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.Icon

private const val TYPE_ID = "LateRunConfigurationTypeForTest"
private const val KNOWN_TYPE_ID = "KnownRunConfigurationTypeForTest"

/** A configuration in the form the IDE writes it, so the reload must not change the file. */
private fun configurationText(name: String = "late"): String = """<configuration name="$name" type="$TYPE_ID" factoryName="$TYPE_ID">
  <option name="value" value="42" />
  <method v="2" />
</configuration>"""

/**
 * A shared run configuration loads with the [UnknownConfigurationType] when a dynamic plugin registers its type later.
 * The registration of the type must give the configuration its type, the way it does for a workspace configuration.
 */
class RunManagerLateConfigurationTypeTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val projectModel: ProjectModelRule = ProjectModelRule()

  @JvmField
  @Rule
  val tempDir: TemporaryDirectory = TemporaryDirectory()

  @JvmField
  @Rule
  val logging = TestLoggerFactory.createTestWatcher()

  /** The project opens with the file in place, because the project open creates the run manager. */
  @Test
  fun `a configuration stored in the runConfigurations directory gets its type`() {
    runBlocking {
      createOrLoadProject(tempDir, projectCreator = { dir ->
        VfsTestUtil.createFile(dir, ".idea/runConfigurations/late.xml", sharedFileText())
        dir.toNioPath()
      }) { project ->
        val runManager = RunManagerImpl.getInstanceImpl(project)
        val settings = runManager.allSettings.single()
        assertThat(settings.type).isSameAs(UnknownConfigurationType.getInstance())
        assertThat(settings.isStoredInDotIdeaFolder).isTrue()
        val unknownId = settings.uniqueID
        val changedIds = mutableListOf<String?>()
        project.messageBus.connect(projectModel.disposableRule.disposable).subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
          override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings, existingId: String?) {
            changedIds.add(existingId)
          }
        })

        val type = registerType()

        assertThat(runManager.allSettings.single()).isSameAs(settings)
        assertThat(settings.type).isSameAs(type)
        assertThat(settings.isStoredInDotIdeaFolder).isTrue()
        assertThat(settings.uniqueID).isEqualTo("$TYPE_ID.late")
        assertThat((settings.configuration as LateRunConfigurationForTest).value).isEqualTo("42")
        assertThat(runManager.findConfigurationByTypeAndName(type, "late")).isSameAs(settings)
        assertThat(runManager.getConfigurationSettingsList(type)).containsExactly(settings)
        assertThat(changedIds).containsExactly(unknownId)
        assertThat(JDOMUtil.write((settings as RunnerAndConfigurationSettingsImpl).writeScheme())).isEqualTo(configurationText())
      }
    }
  }

  @Test
  fun `a configuration stored in a run_xml file gets its type`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("m")
    val module = projectModel.createModule("m")
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
    projectModel.baseProjectDir.newVirtualFile("m/late.run.xml", sharedFileText().toByteArray())
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    val runManager = RunManager.getInstance(projectModel.project)
    val settings = runManager.allSettings.single()
    assertThat(settings.type).isSameAs(UnknownConfigurationType.getInstance())
    assertThat(settings.isStoredInArbitraryFileInProject).isTrue()

    val type = registerType()

    assertThat(runManager.allSettings.single()).isSameAs(settings)
    assertThat(settings.type).isSameAs(type)
    assertThat(settings.isStoredInArbitraryFileInProject).isTrue()
    assertThat((settings.configuration as LateRunConfigurationForTest).value).isEqualTo("42")
  }

  /** A type missing in this IDE must not show a twin of the runnable configuration that stands for it; the dialog still edits it. */
  @Test
  fun `an unknown configuration with the name of a known one is hidden from run lists`() {
    // registered before the project opens: a type registered later reloads the run manager state
    val knownType = registerType(KNOWN_TYPE_ID)
    runBlocking {
      createOrLoadProject(tempDir, projectCreator = { dir ->
        VfsTestUtil.createFile(dir, ".idea/runConfigurations/twin.xml", sharedFileText("twin"))
        VfsTestUtil.createFile(dir, ".idea/runConfigurations/alone.xml", sharedFileText("alone"))
        dir.toNioPath()
      }) { project ->
        val runManager = RunManagerImpl.getInstanceImpl(project)
        val unknownTwin = runManager.allSettings.single { it.name == "twin" }
        val unknownAlone = runManager.allSettings.single { it.name == "alone" }
        val known = runManager.createConfiguration("twin", knownType.configurationFactories.single())
        runManager.addConfiguration(known)
        assertThat(listOf(unknownTwin, unknownAlone).map { it.type }).containsOnly(UnknownConfigurationType.getInstance())

        assertThat(runManager.getConfigurationsGroupedByTypeAndFolder(true).flattenSettings()).containsExactlyInAnyOrder(known, unknownAlone)
        assertThat(runManager.getConfigurationsGroupedByTypeAndFolder(false).flattenSettings()).containsExactly(known)
        assertThat(runManager.getAllConfigurationsGroupedByTypeAndFolder().flattenSettings())
          .containsExactlyInAnyOrder(known, unknownTwin, unknownAlone)
        assertThat(runManager.allSettings).contains(unknownTwin)
      }
    }
  }

  /** The run widget popup shows all unknown configurations as one group node. */
  @Test
  fun `unknown configurations take one group node in the run widget popup`() {
    val knownType = registerType(KNOWN_TYPE_ID)
    runBlocking {
      createOrLoadProject(tempDir, projectCreator = { dir ->
        VfsTestUtil.createFile(dir, ".idea/runConfigurations/u1.xml", sharedFileText("u1"))
        VfsTestUtil.createFile(dir, ".idea/runConfigurations/u2.xml", sharedFileText("u2"))
        dir.toNioPath()
      }) { project ->
        val runManager = RunManagerImpl.getInstanceImpl(project)
        runManager.allSettings.single { it.name == "u2" }.folderName = "f"
        val known = runManager.createConfiguration("known", knownType.configurationFactories.single())
        runManager.addConfiguration(known)

        val group = createRunConfigurationActionGroup(runManager.getConfigurationsGroupedByTypeAndFolder(true), emptySet(), false) { cfg ->
          object : AnAction(cfg.name) {
            override fun actionPerformed(e: AnActionEvent) = Unit
          }
        } as ActionGroup
        val typeGroups = group.getChildren(null).filterIsInstance<ActionGroup>()
        assertThat(typeGroups).hasSize(2)
        val rows = typeGroups.flatMap { it.getChildren(null).asList() }

        val unknownGroup = rows.filterIsInstance<ActionGroup>().single()
        val title = ExecutionBundle.message("run.toolbar.widget.dropdown.unknown.configurations.text")
        assertThat(unknownGroup.templatePresentation.isPopupGroup).isTrue()
        assertThat(unknownGroup.templatePresentation.text).isEqualTo(title)
        assertThat(unknownGroup.templatePresentation.icon).isSameAs(AllIcons.Actions.Help)
        assertThat(unknownGroup.getChildren(null).map { it.templateText }).containsExactlyInAnyOrder("u1", "u2")

        val hidden = rows.filter { it.templatePresentation.getClientProperty(ActionUtil.SEARCH_TAG) == "hidden" }
        assertThat(hidden.map { it.templateText }).containsExactlyInAnyOrder("u1", "u2")
        assertThat(hidden.map { it.templatePresentation.getClientProperty(ActionUtil.SECONDARY_TEXT) }).containsOnly(title)
        assertThat(rows.filter { it !is ActionGroup }.map { it.templateText }).containsExactlyInAnyOrder("known", "u1", "u2")
      }
    }
  }

  private fun Map<ConfigurationType, Map<String?, List<RunnerAndConfigurationSettings>>>.flattenSettings(): List<RunnerAndConfigurationSettings> {
    return values.flatMap { folders -> folders.values.flatten() }
  }

  private fun registerType(id: String = TYPE_ID): ConfigurationType {
    val type = LateConfigurationTypeForTest(id)
    ConfigurationType.CONFIGURATION_TYPE_EP.point.registerExtension(type, projectModel.disposableRule.disposable)
    return type
  }

  private fun sharedFileText(name: String = "late"): String = """
    <component name="ProjectRunConfigurationManager">
      ${configurationText(name)}
    </component>""".trimIndent()
}

private class LateConfigurationTypeForTest(private val typeId: String) : ConfigurationType {
  private val factory = object : ConfigurationFactory(this) {
    override fun getId(): String = typeId

    override fun createTemplateConfiguration(project: Project): RunConfiguration = LateRunConfigurationForTest(project, this)

    override fun getOptionsClass(): Class<out BaseState> = LateOptionsForTest::class.java
  }

  override fun getId(): String = typeId

  override fun getDisplayName(): String = typeId

  override fun getConfigurationTypeDescription(): String = typeId

  override fun getIcon(): Icon? = null

  override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

private class LateOptionsForTest : RunConfigurationOptions() {
  var value: String? by string()
}

private class LateRunConfigurationForTest(project: Project, factory: ConfigurationFactory)
  : RunConfigurationBase<LateOptionsForTest>(project, factory, "") {
  val value: String?
    get() = (options as LateOptionsForTest).value

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = throw UnsupportedOperationException()

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.TechnologyFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An inspection profile of a project holds only the tools of the modules that [TechnologyFilter] accepts.
 */
@TestApplication
internal class InspectionProfileTechsTest {
  companion object {
    private val projectFixture = projectFixture()
    private val otherProjectFixture = projectFixture()
  }

  private val project: Project
    get() = projectFixture.get()

  private val otherProject: Project
    get() = otherProjectFixture.get()

  private val plainModule = DefaultPluginDescriptor(PluginId.getId("technology.plain"), javaClass.classLoader)
  private val javaModule = DefaultPluginDescriptor(PluginId.getId("technology.java"), javaClass.classLoader)

  private val appProfileManager: BaseInspectionProfileManager
    get() = InspectionProfileManager.getInstance() as BaseInspectionProfileManager

  @TestDisposable
  lateinit var testDisposable: Disposable

  @BeforeEach
  fun setUp() {
    @Suppress("TestOnlyProblems")
    InspectionProfileImpl.INIT_INSPECTIONS = true
    Disposer.register(testDisposable) {
      @Suppress("TestOnlyProblems")
      InspectionProfileImpl.INIT_INSPECTIONS = false
      for (shortName in listOf(PLAIN_TOOL, JAVA_TOOL, PROVIDER_TOOL, GROUP_TOOL, GROUP_CHILD)) {
        HighlightDisplayKey.unregister(shortName)
      }
    }
  }

  @Test
  fun `a profile of a project holds only the applicable tools`() {
    val filter = installFilter(applicable = setOf(plainModule.pluginId))
    val profile = projectProfile(listOf(extensionTool(PLAIN_TOOL, plainModule), extensionTool(JAVA_TOOL, javaModule)))

    assertEquals(setOf(PLAIN_TOOL), shortNames(profile.tools))
    assertNull(profile.getToolsOrNull(JAVA_TOOL, project))

    filter.applicable = setOf(plainModule.pluginId, javaModule.pluginId)
    publishFilterChange(project)
    assertEquals(setOf(PLAIN_TOOL, JAVA_TOOL), shortNames(profile.tools))

    // a change of another project does nothing to the profile
    filter.applicable = setOf(plainModule.pluginId)
    publishFilterChange(otherProject)
    assertEquals(setOf(PLAIN_TOOL, JAVA_TOOL), shortNames(profile.tools))
  }

  @Test
  fun `a removed tool keeps its changed settings until the tech is enabled again`() {
    val filter = installFilter(applicable = setOf(plainModule.pluginId, javaModule.pluginId))
    val profile = projectProfile(listOf(extensionTool(PLAIN_TOOL, plainModule), extensionTool(JAVA_TOOL, javaModule)))
    profile.setErrorLevel(HighlightDisplayKey.find(JAVA_TOOL)!!, HighlightDisplayLevel.ERROR, project)

    filter.applicable = setOf(plainModule.pluginId)
    publishFilterChange(project)
    assertNull(profile.getToolsOrNull(JAVA_TOOL, project))

    filter.applicable = setOf(plainModule.pluginId, javaModule.pluginId)
    publishFilterChange(project)
    assertEquals(HighlightDisplayLevel.ERROR, profile.getTools(JAVA_TOOL, project).level)
  }

  @Test
  fun `a tool of a tool provider follows the module of its provider`() {
    val registrar = InspectionToolRegistrar.getInstance()
    ApplicationManager.getApplication().extensionArea
      .getExtensionPoint<InspectionToolProvider>("com.intellij.inspectionToolProvider")
      .registerExtension(InspectionToolProvider { arrayOf(ProviderTool::class.java) }, javaModule, testDisposable)
    val providerTool = registrar.createTools().single { it.shortName == PROVIDER_TOOL }
    assertNull(providerTool.extension)

    val filter = installFilter(applicable = setOf(plainModule.pluginId))
    val profile = projectProfile(listOf(extensionTool(PLAIN_TOOL, plainModule), providerTool))
    assertEquals(setOf(PLAIN_TOOL), shortNames(profile.tools))

    filter.applicable = setOf(plainModule.pluginId, javaModule.pluginId)
    publishFilterChange(project)
    assertEquals(setOf(PLAIN_TOOL, PROVIDER_TOOL), shortNames(profile.tools))
  }

  @Test
  fun `the children of a dynamic group tool follow their group tool`() {
    val filter = installFilter(applicable = setOf(plainModule.pluginId, javaModule.pluginId))
    val groupTool = LocalInspectionToolWrapper(GroupTool()).also { it.setProviderModule(javaModule) }
    val profile = projectProfile(listOf(extensionTool(PLAIN_TOOL, plainModule), groupTool))
    assertEquals(setOf(PLAIN_TOOL, GROUP_TOOL, GROUP_CHILD), shortNames(profile.tools))
    profile.setErrorLevel(HighlightDisplayKey.find(GROUP_CHILD)!!, HighlightDisplayLevel.ERROR, project)

    filter.applicable = setOf(plainModule.pluginId)
    publishFilterChange(project)
    assertEquals(setOf(PLAIN_TOOL), shortNames(profile.tools))

    filter.applicable = setOf(plainModule.pluginId, javaModule.pluginId)
    publishFilterChange(project)
    assertEquals(setOf(PLAIN_TOOL, GROUP_TOOL, GROUP_CHILD), shortNames(profile.tools))
    assertEquals(HighlightDisplayLevel.ERROR, profile.getTools(GROUP_CHILD, project).level)
  }

  /** A profile that the manager of [project] owns. Its base profile is a shared profile with the same tools. */
  private fun projectProfile(tools: List<InspectionToolWrapper<*, *>>): InspectionProfileImpl {
    val supplier = InspectionToolsSupplier.Simple(tools)
    val base = InspectionProfileImpl("base", supplier, appProfileManager, null, null)
    val profile = InspectionProfileImpl("of project", supplier, ProjectInspectionProfileManager.getInstance(project), base, null)
    profile.initInspectionTools(project)
    // the profile stops its listeners, so it does not answer the filter change of a later test
    Disposer.register(testDisposable) { profile.cleanup(project) }
    return profile
  }

  /** A tool that [module] registers as an extension. */
  private fun extensionTool(shortName: String, module: PluginDescriptor): LocalInspectionToolWrapper {
    val extension = LocalInspectionEP()
    extension.implementationClass = PlainTool::class.java.name
    extension.shortName = shortName
    extension.displayName = shortName
    extension.groupDisplayName = "Techs"
    extension.enabledByDefault = true
    extension.pluginDescriptor = module
    return LocalInspectionToolWrapper(extension)
  }

  private fun installFilter(applicable: Set<PluginId>): FakeFilter {
    val filter = FakeFilter(project, applicable)
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(TechnologyFilter::class.java, filter, testDisposable)
    return filter
  }

  /** Publishes the change as the technology module does: in a write action on the EDT. */
  private fun publishFilterChange(changedProject: Project) {
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(TechnologyFilter.TOPIC)
    timeoutRunBlocking {
      edtWriteAction {
        publisher.applicabilityChanged(changedProject)
      }
    }
  }

  private fun shortNames(tools: Collection<Tools>): Set<String> = tools.mapTo(HashSet()) { it.shortName }
}

private const val PLAIN_TOOL = "TechsPlainTool"
private const val JAVA_TOOL = "TechsJavaTool"
private const val PROVIDER_TOOL = "TechsProviderTool"
private const val GROUP_TOOL = "TechsGroupTool"
private const val GROUP_CHILD = "TechsGroupChild"

/** Restricts [project] to the modules of [applicable]. */
private class FakeFilter(private val project: Project, @Volatile var applicable: Set<PluginId>) : TechnologyFilter {
  override fun isRestricted(project: Project): Boolean = project === this.project

  override fun isApplicable(module: PluginDescriptor, project: Project): Boolean {
    return project !== this.project || module.pluginId in applicable
  }
}

private class PlainTool : LocalInspectionTool()

private open class NamedTool(private val name: String) : LocalInspectionTool() {
  override fun getShortName(): String = name
  override fun getDisplayName(): String = name
  override fun getGroupDisplayName(): String = "Techs"
  override fun isEnabledByDefault(): Boolean = true
}

private class ProviderTool : NamedTool(PROVIDER_TOOL)

private class GroupChild : NamedTool(GROUP_CHILD)

private class GroupTool : NamedTool(GROUP_TOOL), DynamicGroupTool {
  override fun getChildren(): List<LocalInspectionToolWrapper> = listOf(LocalInspectionToolWrapper(GroupChild()))
}

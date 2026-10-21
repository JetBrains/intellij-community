// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.compound.CompoundRunConfigurationType
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.idea.TestFor
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.Icon

/** The id of a run configuration type that no longer exists, as a stored element still spells it. */
private const val REMOVED_TYPE_ID = "RemovedRunConfigurationTypeForTest"

/**
 * A stored run configuration of a removed type must reach a [RunConfigurationConverter], whatever file holds it.
 * Otherwise, it resolves to [com.intellij.execution.configurations.UnknownConfigurationType] and the user cannot run it.
 */
class RunConfigurationConverterTest {
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
  val logging = TestLoggerFactory.createTestWatcher()

  @Before
  fun registerConverter() {
    // The converter is found through the extension list, so the test type only has to be in it.
    ExtensionTestUtil.addExtensions(
      ConfigurationType.CONFIGURATION_TYPE_EP,
      listOf(ConverterConfigurationTypeForTest()),
      projectModel.disposableRule.disposable,
      fireEvents = false,
    )
  }

  @Test
  fun `a configuration stored in the workspace is converted`() {
    val runManager = RunManagerImpl.getInstanceImpl(projectModel.project)

    runManager.loadState(JDOMUtil.load("""
      <component name="RunManager">
        ${configurationElementText("in workspace")}
      </component>""".trimIndent()))

    assertThat(runManager.allSettings.single().type).isInstanceOf(CompoundRunConfigurationType::class.java)
  }

  @Test
  @TestFor(issues = ["PY-93333"])
  fun `a configuration stored in a run_xml file is converted`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("m")
    val module = projectModel.createModule("m")
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)

    projectModel.baseProjectDir.newVirtualFile("m/legacy.run.xml", """
      <component name="ProjectRunConfigurationManager">
        ${configurationElementText("in legacy.run.xml")}
      </component>""".trimIndent().toByteArray())
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    val settings = RunManager.getInstance(projectModel.project).allSettings.single()
    assertThat(settings.type).isInstanceOf(CompoundRunConfigurationType::class.java)
    assertThat(settings.isStoredInArbitraryFileInProject).isTrue()
  }

  private fun configurationElementText(name: String): String =
    """<configuration name="$name" type="$REMOVED_TYPE_ID" factoryName="$REMOVED_TYPE_ID"/>"""
}

/**
 * Retypes an element of the removed [REMOVED_TYPE_ID] as a compound one, the way a production converter retypes a
 * configuration of a type that its plugin no longer has.
 */
private class ConverterConfigurationTypeForTest : ConfigurationType, RunConfigurationConverter {
  override fun getId(): String = "ConverterRunConfigurationTypeForTest"

  override fun getDisplayName(): String = id

  override fun getConfigurationTypeDescription(): String = id

  override fun getIcon(): Icon? = null

  override fun getConfigurationFactories(): Array<ConfigurationFactory> = emptyArray()

  override fun convertRunConfigurationOnDemand(element: Element): Boolean {
    if (element.getAttributeValue("type") != REMOVED_TYPE_ID) {
      return false
    }

    val type = runConfigurationType<CompoundRunConfigurationType>()
    element.setAttribute("type", type.id)
    element.setAttribute("factoryName", type.id)
    return true
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.tree.TreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

@TestApplication
internal class InlaySettingsPanelTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project by projectFixture

  @TestDisposable
  private lateinit var disposable: Disposable

  private val language: Language = PlainTextLanguage.INSTANCE
  private val first = FakeModel("first", language)
  private val second = FakeModel("second", language)
  private var lastViewedProviderId: String? = null

  @BeforeEach
  fun setUp() {
    val provider = object : InlaySettingsProvider {
      override fun createModels(project: Project, language: Language): List<InlayProviderSettingsModel> = listOf(first, second)
      override fun getSupportedLanguages(project: Project): Collection<Language> = listOf(language)
    }
    ExtensionTestUtil.maskExtensions(InlaySettingsProvider.EP.EXTENSION_POINT_NAME, listOf(provider), disposable)
    ExtensionTestUtil.maskExtensions(InlayGroupSettingProvider.EP.EXTENSION_POINT_NAME, emptyList(), disposable)
    val settings = InlayHintsSettings.instance()
    lastViewedProviderId = settings.getLastViewedProviderId()
    assertThat(settings.hintsEnabledGlobally()).isTrue()
    assertThat(settings.hintsEnabled(language)).isTrue()
  }

  @AfterEach
  fun tearDown() {
    lastViewedProviderId?.let { InlayHintsSettings.instance().saveLastViewedProviderId(it) }
  }

  @Test
  fun `the language node is unchecked after reset when all its providers are disabled`() = runInEdtAndWait {
    first.stored = false
    second.stored = false
    val panel = InlaySettingsPanel(project)

    panel.reset()

    assertThat(panel.node(language).isChecked).isFalse()
    assertThat(panel.node(InlayGroup.OTHER_GROUP).isChecked).isFalse()
    assertThat(panel.isModified()).isFalse()
  }

  @Test
  fun `the language node is checked after reset when one of its providers is enabled`() = runInEdtAndWait {
    first.stored = true
    second.stored = false
    val panel = InlaySettingsPanel(project)

    panel.reset()

    assertThat(panel.node(language).isChecked).isTrue()
    assertThat(panel.node(InlayGroup.OTHER_GROUP).isChecked).isTrue()
    assertThat(panel.isModified()).isFalse()
  }

  @Test
  fun `clearing the last provider clears the language and group nodes`() = runInEdtAndWait {
    first.stored = true
    second.stored = true
    val panel = InlaySettingsPanel(project)
    panel.reset()

    panel.tree.setNodeState(panel.node(first), false)
    assertThat(panel.node(language).isChecked).isTrue()
    assertThat(panel.node(InlayGroup.OTHER_GROUP).isChecked).isTrue()

    panel.tree.setNodeState(panel.node(second), false)
    assertThat(panel.node(language).isChecked).isFalse()
    assertThat(panel.node(InlayGroup.OTHER_GROUP).isChecked).isFalse()
  }

  @Test
  fun `disabled providers stay unchecked after apply and reset`() = runInEdtAndWait {
    first.stored = true
    second.stored = true
    val panel = InlaySettingsPanel(project)
    panel.reset()
    panel.tree.setNodeState(panel.node(language), false)
    assertThat(panel.isModified()).isTrue()

    panel.apply()
    panel.reset()

    assertThat(first.stored).isFalse()
    assertThat(second.stored).isFalse()
    assertThat(InlayHintsSettings.instance().hintsEnabled(language)).isTrue()
    assertThat(panel.node(language).isChecked).isFalse()
    assertThat(panel.isModified()).isFalse()
  }

  private fun InlaySettingsPanel.node(userObject: Any): CheckedTreeNode =
    TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) { it.userObject == userObject } as CheckedTreeNode

  private class FakeModel(id: String, language: Language) : InlayProviderSettingsModel(true, id, language) {
    var stored: Boolean = true

    override val name: String = id
    override val component: JComponent = JPanel()
    override val description: String? = null
    override val previewText: String? = null
    @Deprecated("Not used in new UI")
    override val mainCheckBoxLabel: String = id
    override val cases: List<ImmediateConfigurable.Case> = emptyList()

    override fun getCasePreview(case: ImmediateConfigurable.Case?): String? = null
    override fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language? = null
    override fun getCaseDescription(case: ImmediateConfigurable.Case): String? = null

    override fun apply() {
      stored = isEnabled
    }

    override fun isModified(): Boolean = isEnabled != stored

    override fun reset() {
      isEnabled = stored
    }
  }
}

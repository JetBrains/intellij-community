// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.FeatureButtonModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.FeatureButtonModelWithBackend
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
internal class WelcomeScreenFeatureGridTest {
  private val project: Project get() = ProjectManager.getInstance().defaultProject

  @Test
  fun aWithdrawnFeatureLosesItsKeyedButton(): Unit = timeoutRunBlocking {
    val offeredFeatures = offeredFeatures(
      project = project,
      registeredFeatureIds = listOf(AGENT_SESSIONS, TERMINAL),
      features = listOf(TestFeature(AGENT_SESSIONS, available = false), TestFeature(TERMINAL, available = true)),
    )

    val models = visibleFeatureButtonModels(
      models = listOf(keyedButton(AGENT_SESSIONS), keyedButton(TERMINAL), NEW_FILE_BUTTON),
      offeredFeatures = offeredFeatures,
      sectionFeatureKeys = emptySet(),
      featureKeysReplacingFeatureGrid = emptySet(),
    )

    assertEquals(listOf(TERMINAL, NEW_FILE), models.map { it.text })
  }

  /** A withdrawn feature is not offered, even while it states that it needs no handler. */
  @Test
  fun aWithdrawnFeatureIsNotOfferedEvenWhenAlwaysAvailable(): Unit = timeoutRunBlocking {
    val offeredFeatures = offeredFeatures(
      project = project,
      registeredFeatureIds = emptyList(),
      features = listOf(TestFeature(AGENT_SESSIONS, available = false), TestFeature(BANNER, available = true)),
    )

    assertFalse(offeredFeatures.isOffered(AGENT_SESSIONS, isAlwaysAvailable = true))
    assertTrue(offeredFeatures.isOffered(BANNER, isAlwaysAvailable = true))
    assertFalse(offeredFeatures.isOffered(BANNER, isAlwaysAvailable = false))
  }

  @Test
  fun aReplacingSectionEmptiesTheGrid() {
    val models = visibleFeatureButtonModels(
      models = listOf(keyedButton(TERMINAL), NEW_FILE_BUTTON),
      offeredFeatures = OfferedFeatures(registeredFeatureIds = setOf(TERMINAL), withdrawnFeatureKeys = emptySet()),
      sectionFeatureKeys = setOf(AGENT_SESSIONS, BANNER),
      featureKeysReplacingFeatureGrid = setOf(AGENT_SESSIONS),
    )

    assertEquals(emptyList<FeatureButtonModel>(), models)
  }

  /** A banner section is decoration, so the grid stays under it. */
  @Test
  fun aSectionThatReplacesNothingKeepsTheGrid() {
    val models = visibleFeatureButtonModels(
      models = listOf(keyedButton(TERMINAL), NEW_FILE_BUTTON),
      offeredFeatures = OfferedFeatures(registeredFeatureIds = setOf(TERMINAL), withdrawnFeatureKeys = emptySet()),
      sectionFeatureKeys = setOf(BANNER),
      featureKeysReplacingFeatureGrid = setOf(AGENT_SESSIONS),
    )

    assertEquals(listOf(TERMINAL, NEW_FILE), models.map { it.text })
  }

  /** The replacing feature states no section, for example while it is not available, so the buttons come back. */
  @Test
  fun noSectionKeepsTheGrid() {
    val models = visibleFeatureButtonModels(
      models = listOf(keyedButton(TERMINAL), NEW_FILE_BUTTON),
      offeredFeatures = OfferedFeatures(registeredFeatureIds = setOf(TERMINAL), withdrawnFeatureKeys = setOf(AGENT_SESSIONS)),
      sectionFeatureKeys = emptySet(),
      featureKeysReplacingFeatureGrid = setOf(AGENT_SESSIONS),
    )

    assertEquals(listOf(TERMINAL, NEW_FILE), models.map { it.text })
  }
}

private const val AGENT_SESSIONS = "air.sessions"
private const val TERMINAL = "terminal.toolwindow"
private const val BANNER = "trial.started.banner"
private const val NEW_FILE = "New File"

/** A button without a feature key, which the grid always shows. */
private val NEW_FILE_BUTTON = FeatureButtonModel(text = NEW_FILE, icon = EmptyIcon.ICON_16, onClick = { _, _ -> })

/** A button whose text is its feature key, so an assertion reads which features kept their button. */
private fun keyedButton(featureKey: String): FeatureButtonModel {
  return FeatureButtonModelWithBackend(featureKey = featureKey, text = featureKey, icon = EmptyIcon.ICON_16)
}

private class TestFeature(override val featureKey: String, private val available: Boolean) : WelcomeScreenFeatureUI() {
  override val icon: Icon get() = EmptyIcon.ICON_16

  override suspend fun isAvailable(project: Project): Boolean = available
}

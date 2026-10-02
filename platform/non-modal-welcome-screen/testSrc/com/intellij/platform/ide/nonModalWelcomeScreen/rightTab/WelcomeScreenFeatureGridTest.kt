// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.FeatureButtonModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.FeatureButtonModelWithBackend
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureUI.Content
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon
import javax.swing.JPanel

@TestApplication
internal class WelcomeScreenFeatureGridTest {
  private val project: Project get() = ProjectManager.getInstance().defaultProject

  @Test
  fun aWithdrawnFeatureStatesNoSectionAndLosesItsKeyedButton(): Unit = timeoutRunBlocking {
    val createContentCalled = CompletableDeferred<Unit>()
    val features = listOf(
      TestFeature(AGENT_SESSIONS, available = false) {
        createContentCalled.complete(Unit)
        Content(JPanel())
      },
      TestFeature(TERMINAL, available = true),
    )
    withOwner { owner ->
      val outcomes = prepareFeatures(project, features, registered(AGENT_SESSIONS, TERMINAL), owner)

      assertNull(outcomes.single { it.featureKey == AGENT_SESSIONS }.section)
      assertFalse(createContentCalled.isCompleted)
      val models = visibleFeatureButtonModels(
        models = listOf(keyedButton(AGENT_SESSIONS), keyedButton(TERMINAL), NEW_FILE_BUTTON),
        offeredFeatures = offeredFeatures(outcomes, setOf(AGENT_SESSIONS, TERMINAL)),
        sectionFeatureKeys = emptySet(),
        featureKeysReplacingFeatureGrid = emptySet(),
      )
      assertEquals(listOf(TERMINAL, NEW_FILE), models.map { it.text })
    }
  }

  @Test
  fun anAlwaysAvailableFeatureIsOfferedWithoutAHandler(): Unit = timeoutRunBlocking {
    val features = listOf(TestFeature(BANNER, isAlwaysAvailable = true) { Content(JPanel()) })
    withOwner { owner ->
      val outcomes = prepareFeatures(project, features, registered(), owner)

      assertEquals(listOf(BANNER), outcomes.mapNotNull { it.section?.featureKey })
      val offeredFeatures = offeredFeatures(outcomes, emptySet())
      assertTrue(offeredFeatures.isOffered(BANNER, isAlwaysAvailable = true))
      assertFalse(offeredFeatures.isOffered(BANNER, isAlwaysAvailable = false))
    }
  }

  /** A withdrawn feature is not offered, even while it states that it needs no handler. */
  @Test
  fun aWithdrawnFeatureIsNotOfferedEvenWhenAlwaysAvailable(): Unit = timeoutRunBlocking {
    val features = listOf(TestFeature(AGENT_SESSIONS, available = false, isAlwaysAvailable = true) { Content(JPanel()) })
    withOwner { owner ->
      val outcomes = prepareFeatures(project, features, registered(), owner)

      assertNull(outcomes.single().section)
      assertFalse(offeredFeatures(outcomes, emptySet()).isOffered(AGENT_SESSIONS, isAlwaysAvailable = true))
    }
  }

  @Test
  fun aFeatureWithoutAHandlerStatesNoSection(): Unit = timeoutRunBlocking {
    val createContentCalled = CompletableDeferred<Unit>()
    val features = listOf(
      TestFeature(TERMINAL) {
        createContentCalled.complete(Unit)
        Content(JPanel())
      },
    )
    withOwner { owner ->
      val outcomes = prepareFeatures(project, features, registered(AGENT_SESSIONS), owner)

      assertTrue(outcomes.single().isAvailable)
      assertNull(outcomes.single().section)
      assertFalse(createContentCalled.isCompleted)
    }
  }

  /** The feature ids never come, so an always-available feature that waits for them would never finish. */
  @Test
  fun anAlwaysAvailableFeatureDoesNotWaitForTheFeatureIds(): Unit = timeoutRunBlocking {
    val registeredFeatureIds = CompletableDeferred<Set<String>>()
    val features = listOf(TestFeature(BANNER, isAlwaysAvailable = true) { Content(JPanel()) })
    withOwner { owner ->
      try {
        val outcomes = prepareFeatures(project, features, registeredFeatureIds, owner)

        assertNotNull(outcomes.single().section)
        assertFalse(registeredFeatureIds.isCompleted)
      }
      finally {
        registeredFeatureIds.cancel()
      }
    }
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

  /** The first section waits for the second one, so a sequential call would never finish. */
  @Test
  fun sectionsAreCreatedTogetherAndKeepTheContentOrder(): Unit = timeoutRunBlocking {
    val secondStarted = CompletableDeferred<Unit>()
    val features = listOf(
      TestFeature(TERMINAL, contentOrder = 2) {
        secondStarted.complete(Unit)
        Content(JPanel())
      },
      TestFeature(AGENT_SESSIONS, contentOrder = 1) {
        secondStarted.await()
        Content(JPanel())
      },
    )
    withOwner { owner ->
      val outcomes = prepareFeatures(project, features, registeredKeysOf(features), owner)

      assertEquals(listOf(AGENT_SESSIONS, TERMINAL), outcomes.mapNotNull { it.section?.featureKey })
    }
  }

  @Test
  fun aFailingFeatureDoesNotStopTheOtherSections() {
    val features = listOf(
      TestFeature(AGENT_SESSIONS, contentOrder = 1) { throw IllegalStateException("broken section") },
      TestFeature(TERMINAL, contentOrder = 2) { Content(JPanel()) },
    )
    val owner = Disposer.newDisposable()
    try {
      lateinit var outcomes: List<FeatureOutcome>

      val error = LoggedErrorProcessor.executeAndReturnLoggedError {
        outcomes = timeoutRunBlocking { prepareFeatures(project, features, registeredKeysOf(features), owner) }
      }

      assertEquals("broken section", error.message)
      assertEquals(listOf(TERMINAL), outcomes.mapNotNull { it.section?.featureKey })
    }
    finally {
      Disposer.dispose(owner)
    }
  }

  /**
   * On the `runBlocking` event loop, the second feature starts only after the first feature returned its section, because the first
   * feature does not suspend.
   */
  @Test
  fun aCancelledPreparationLeavesTheReturnedSectionUnderTheOwner(): Unit = timeoutRunBlocking {
    val disposable = Disposer.newCheckedDisposable()
    val secondStarted = CompletableDeferred<Unit>()
    val features = listOf(
      TestFeature(TERMINAL, contentOrder = 1) { Content(JPanel(), disposable = disposable) },
      TestFeature(AGENT_SESSIONS, contentOrder = 2) {
        secondStarted.complete(Unit)
        awaitCancellation()
      },
    )
    withOwner { owner ->
      val job = launch { prepareFeatures(project, features, registeredKeysOf(features), owner) }
      secondStarted.await()
      job.cancelAndJoin()
      assertFalse(disposable.isDisposed)

      Disposer.dispose(owner)

      assertTrue(disposable.isDisposed)
    }
  }
}

/** Runs [block] with a new owner, and disposes the owner after it. */
private suspend fun <T> withOwner(block: suspend (owner: Disposable) -> T): T {
  val owner = Disposer.newDisposable()
  try {
    return block(owner)
  }
  finally {
    Disposer.dispose(owner)
  }
}

private fun registered(vararg featureKeys: String): CompletableDeferred<Set<String>> = CompletableDeferred(featureKeys.toHashSet())

private fun registeredKeysOf(features: List<WelcomeScreenFeatureUI>): CompletableDeferred<Set<String>> {
  return CompletableDeferred(features.mapTo(HashSet()) { it.featureKey })
}

/** Builds the rule of the feature buttons from [outcomes], as `prepareDefaultBody` does. */
private fun offeredFeatures(outcomes: List<FeatureOutcome>, registeredFeatureIds: Set<String>): OfferedFeatures {
  return OfferedFeatures(
    registeredFeatureIds = registeredFeatureIds,
    withdrawnFeatureKeys = outcomes.filter { !it.isAvailable }.mapTo(HashSet()) { it.featureKey },
  )
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

private class TestFeature(
  override val featureKey: String,
  private val available: Boolean = true,
  override val contentOrder: Int = 0,
  override val isAlwaysAvailable: Boolean = false,
  private val content: (suspend () -> Content?)? = null,
) : WelcomeScreenFeatureUI() {
  override val icon: Icon get() = EmptyIcon.ICON_16

  override suspend fun isAvailable(project: Project): Boolean = available

  override suspend fun createContent(project: Project): Content? = content?.invoke()
}

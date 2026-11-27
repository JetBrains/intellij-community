// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.cutText
import androidx.compose.ui.semantics.pasteText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import io.mockk.every
import io.mockk.mockk
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.foundation.shortcut.ActionInvocation
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The root data provider must sink all three semantic edit providers, so the platform `$Copy`, `$Cut`, and `$Paste`
 * actions enable and perform against focused Compose semantics without any Jewel-specific action registration.
 */
internal class EditProvidersSinkTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `root snapshot sinks copy, cut, and paste providers`() {
        runBlocking {
            val rootDataProviderModifier = RootDataProviderModifier()
            rule.setContent { Box(modifier = rootDataProviderModifier.focusable()) }
            rule.awaitIdle()

            val sink = TestDataSink()
            rootDataProviderModifier.uiDataSnapshot(sink)

            val copyProvider = sink.get<CopyProvider>(PlatformDataKeys.COPY_PROVIDER.name)
            val cutProvider = sink.get<CutProvider>(PlatformDataKeys.CUT_PROVIDER.name)
            val pasteProvider = sink.get<PasteProvider>(PlatformDataKeys.PASTE_PROVIDER.name)
            assertNotNull(copyProvider)
            assertNotNull(cutProvider)
            assertNotNull(pasteProvider)
            assertEquals(ActionUpdateThread.EDT, copyProvider!!.actionUpdateThread)
            assertEquals(ActionUpdateThread.EDT, cutProvider!!.actionUpdateThread)
            assertEquals(ActionUpdateThread.EDT, pasteProvider!!.actionUpdateThread)
        }
    }

    @Test
    fun `copy, cut, and paste providers route to focused JewelActions handlers without emitting Jewel events`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }
        val focus = FocusRequester()
        var bindingsEnabled by mutableStateOf(true)
        var copies = 0
        var cuts = 0
        var pastes = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.shortcut(JewelActions.Copy, enabled = bindingsEnabled) { copies++ }
                        .shortcut(JewelActions.Cut, enabled = bindingsEnabled) { cuts++ }
                        .shortcut(JewelActions.Paste, enabled = bindingsEnabled) { pastes++ }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        val dataContext = DataContext { dataId -> if (JEWEL_SHORTCUT_HOST_STATE.`is`(dataId)) host else null }
        val copyProvider = ComposeCopyProvider()
        val cutProvider = ComposeCutProvider()
        val pasteProvider = ComposePasteProvider()

        val events = mutableListOf<ActionInvocation>()

        @Suppress("InjectDispatcher") val scope = CoroutineScope(Dispatchers.Unconfined)
        host.events.invocations.onEach { events += it }.launchIn(scope)
        JewelActionMappings.clearForTests()
        JewelActionMappings.installStandardMappings()
        try {
            assertTrue(copyProvider.isCopyEnabled(dataContext))
            assertTrue(cutProvider.isCutEnabled(dataContext))
            assertTrue(pasteProvider.isPastePossible(dataContext))
            assertTrue(pasteProvider.isPasteEnabled(dataContext))

            copyProvider.performCopy(dataContext)
            cutProvider.performCut(dataContext)
            pasteProvider.performPaste(dataContext)
            assertEquals(1, copies)
            assertEquals(1, cuts)
            assertEquals(1, pastes)
            assertTrue("mapped edit providers must not emit Jewel lifecycle events", events.isEmpty())

            bindingsEnabled = false
            rule.waitForIdle()
            assertFalse(copyProvider.isCopyEnabled(dataContext))
            assertFalse(cutProvider.isCutEnabled(dataContext))
            assertFalse(pasteProvider.isPastePossible(dataContext))
            assertFalse(pasteProvider.isPasteEnabled(dataContext))
        } finally {
            scope.cancel()
            JewelActionMappings.clearForTests()
            JewelActionMappings.installStandardMappings()
        }
    }

    @Test
    fun `remapping JewelActions Cut skips the Jewel handler in ComposeCutProvider and falls back to semantics`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }
        val focus = FocusRequester()
        var jewelCuts = 0
        var semanticCuts = 0
        lateinit var realWrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { realWrapper = JewelComposePanelWrapper(focusOnClickInside = false) }

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.testTag("remappedCut")
                        .shortcut(JewelActions.Cut) { jewelCuts++ }
                        .semantics {
                            cutText {
                                semanticCuts++
                                true
                            }
                        }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }
        val focusedNode = rule.onNodeWithTag("remappedCut").fetchSemanticsNode()
        val dataContext = DataContext { dataId ->
            when {
                JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> host
                PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> realWrapper
                else -> null
            }
        }
        val cutProvider = ComposeCutProvider { focusedNode }
        val customActionManager =
            mockk<ActionManager> { every { getAction("CustomCutAction") } returns mockk<AnAction>() }

        JewelActionMappings.clearForTests()
        JewelActionMappings.installStandardMappings()
        try {
            JewelActionMappings.map(JewelActions.Cut.id, "CustomCutAction", customActionManager)
            assertTrue(cutProvider.isCutEnabled(dataContext))
            cutProvider.performCut(dataContext)
            assertEquals("remapped JewelActions.Cut must not run via \$Cut provider", 0, jewelCuts)
            assertEquals("ComposeCutProvider must fall back to Compose cutText semantics", 1, semanticCuts)
        } finally {
            JewelActionMappings.clearForTests()
            JewelActionMappings.installStandardMappings()
        }
    }

    @Test
    fun `edit providers fall back to Compose text semantics when CONTEXT_COMPONENT is JewelComposePanelWrapper directly`() {
        lateinit var realWrapper: JewelComposePanelWrapper
        lateinit var childInsideComposePanel: JPanel
        SwingUtilities.invokeAndWait {
            realWrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            childInsideComposePanel = JPanel()
            realWrapper.composePanel.add(childInsideComposePanel)
        }
        assertSame(
            realWrapper.composePanel,
            resolveComposePanel(
                DataContext { if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(it)) realWrapper else null }
            ),
        )
        assertSame(
            realWrapper.composePanel,
            resolveComposePanel(
                DataContext { if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(it)) realWrapper.composePanel else null }
            ),
        )
        assertSame(
            realWrapper.composePanel,
            resolveComposePanel(
                DataContext { if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(it)) childInsideComposePanel else null }
            ),
        )

        val focus = FocusRequester()
        var semanticCopies = 0
        var semanticCuts = 0
        var semanticPastes = 0
        rule.setContent {
            Box(
                Modifier.testTag("editable")
                    .semantics {
                        copyText {
                            semanticCopies++
                            true
                        }
                        cutText {
                            semanticCuts++
                            true
                        }
                        pasteText {
                            semanticPastes++
                            true
                        }
                    }
                    .focusRequester(focus)
                    .focusable()
            )
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }
        val focusedSemanticsNode = rule.onNodeWithTag("editable").fetchSemanticsNode()
        val directWrapperContext = DataContext { dataId ->
            if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId)) realWrapper else null
        }
        val resolveFocused: (androidx.compose.ui.awt.ComposePanel) -> androidx.compose.ui.semantics.SemanticsNode? =
            { panel ->
                assertSame("resolveComposePanel must unwrap JewelComposePanelWrapper", realWrapper.composePanel, panel)
                focusedSemanticsNode
            }

        val copyProvider = ComposeCopyProvider(resolveFocused)
        val cutProvider = ComposeCutProvider(resolveFocused)
        val pasteProvider = ComposePasteProvider(resolveFocused)

        assertTrue(copyProvider.isCopyEnabled(directWrapperContext))
        assertTrue(cutProvider.isCutEnabled(directWrapperContext))
        assertTrue(pasteProvider.isPastePossible(directWrapperContext))
        assertTrue(pasteProvider.isPasteEnabled(directWrapperContext))

        copyProvider.performCopy(directWrapperContext)
        cutProvider.performCut(directWrapperContext)
        pasteProvider.performPaste(directWrapperContext)
        assertEquals(1, semanticCopies)
        assertEquals(1, semanticCuts)
        assertEquals(1, semanticPastes)
    }
}

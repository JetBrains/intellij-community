// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import java.awt.Panel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests covering numpad key normalization, chained shortcut semantics, window focus-loss resets, and host-sampled
 * disabled presentation preservation.
 */
internal class ShortcutHostBehaviorUiTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private val boundFocus = FocusRequester()
    private val claimerFocus = FocusRequester()

    private fun focus(requester: FocusRequester) {
        runBlocking {
            rule.runOnIdle { requester.requestFocus() }
            rule.awaitIdle()
        }
    }

    @Test
    fun `a keymap command on Ctrl plus Enter also runs from Ctrl plus numpad Enter and re-arms on release`() {
        val submit = JewelAction(JewelActionId("test.ui.submit"), "Submit")
        val submitKeymap =
            InMemoryJewelKeymap("ui-test-enter").apply {
                bind(submit.id, JewelKeySequence(JewelKeyStroke(Key.Enter, ctrl = true)))
            }
        val submitHost = JewelShortcutHostState { submitKeymap }
        var submits = 0

        rule.setContent {
            Box(submitHost.resolverRootModifier) {
                Box(
                    Modifier.testTag("form")
                        .shortcut(submit, repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease) { submits++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)

        rule.onNodeWithTag("form").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        rule.waitForIdle()
        assertEquals("Ctrl+Enter did not run the command", 1, submits)

        rule.onNodeWithTag("form").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.NumPadEnter) } }
        rule.waitForIdle()
        assertEquals("Ctrl+numpad Enter did not run the command", 2, submits)

        // Verify releasing NumPadEnter alone (without releasing Ctrl) clears the OnceUntilRelease latch.
        rule.runOnIdle {
            val numpadDown =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_ENTER,
                        '\n',
                        AwtKeyEvent.KEY_LOCATION_NUMPAD,
                    )
                    .toComposeKeyEvent()
            val numpadUp =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_RELEASED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_ENTER,
                        '\n',
                        AwtKeyEvent.KEY_LOCATION_NUMPAD,
                    )
                    .toComposeKeyEvent()
            assertTrue(submitHost.onPreviewKeyEvent(numpadDown))
            assertEquals(3, submits)
            assertTrue(submitHost.onPreviewKeyEvent(numpadDown))
            assertEquals("held repeat must be suppressed", 3, submits)
            assertFalse(submitHost.onPreviewKeyEvent(numpadUp))
            assertTrue(submitHost.onPreviewKeyEvent(numpadDown))
            assertEquals("releasing NumPadEnter must clear OnceUntilRelease latch", 4, submits)
        }
    }

    @Test
    fun `two shortcut modifiers on one node publish both action IDs in modifier chain order`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("empty") }
        val next = JewelAction(JewelActionId("test.ui.next"), "Next Section")
        val previous = JewelAction(JewelActionId("test.ui.previous"), "Previous Section")
        rule.setContent {
            Box(host.resolverRootModifier) { Box(Modifier.testTag("page").shortcut(next) {}.shortcut(previous) {}) }
        }

        val published = rule.onNodeWithTag("page").fetchSemanticsNode().config[JewelShortcutActions]

        assertEquals(listOf(next.id.value, previous.id.value), published)
    }

    @Test
    fun `two claims on one node publish both sequences in modifier chain order`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("empty") }
        val ctrlK = JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true))
        val ctrlJ = JewelKeySequence(JewelKeyStroke(Key.J, ctrl = true))
        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(Modifier.testTag("editor").claimShortcut(ctrlK) {}.claimShortcut(ctrlJ) {})
            }
        }

        val published = rule.onNodeWithTag("editor").fetchSemanticsNode().config[JewelClaimedShortcuts]

        assertEquals(listOf(ctrlK.displayText(), ctrlJ.displayText()), published)
    }

    @Test
    fun `window focus loss resets OnceUntilRelease latch and pending chord`() {
        val fakeWindowInfo =
            object : WindowInfo {
                override var isWindowFocused: Boolean by mutableStateOf(true)
            }
        val chordAction = JewelAction(JewelActionId("test.ui.windowLossChord"), "Chord")
        val localKeymap =
            InMemoryJewelKeymap("window-loss").apply {
                bind(
                    chordAction.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val localHost = JewelShortcutHostState { localKeymap }
        var claimHits = 0

        rule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides fakeWindowInfo) {
                Box(localHost.resolverRootModifier) {
                    Box(
                        Modifier.shortcut(chordAction) {}
                            .claimShortcut(
                                JewelKeySequence(JewelKeyStroke(Key.L, ctrl = true)),
                                repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                            ) {
                                claimHits++
                            }
                            .focusRequester(claimerFocus)
                            .focusable()
                    )
                }
            }
        }
        focus(claimerFocus)

        val ctrlLDown =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_L,
                    AwtKeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()
        val ctrlKDown =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_K,
                    AwtKeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()

        rule.runOnIdle {
            assertTrue(localHost.onPreviewKeyEvent(ctrlLDown))
            assertEquals(1, claimHits)
            assertTrue(localHost.onPreviewKeyEvent(ctrlLDown))
            assertEquals("held repeat must be suppressed before window focus loss", 1, claimHits)
            assertTrue(localHost.onPreviewKeyEvent(ctrlKDown))
            assertTrue(localHost.isAwaitingSecondStroke)

            // Simulate a modal dialog or Alt-Tab/Cmd-Tab taking OS window focus without releasing Compose focus.
            fakeWindowInfo.isWindowFocused = false
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertFalse("pending chord must reset on window focus loss", localHost.isAwaitingSecondStroke)
            fakeWindowInfo.isWindowFocused = true
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertTrue(localHost.onPreviewKeyEvent(ctrlLDown))
            assertEquals("OnceUntilRelease latch must reset on window focus loss", 2, claimHits)
        }
    }

    @Test
    fun `host-sampled disabled presentation stays disabled even when a focused binding exists`() {
        val mapped = JewelAction(JewelActionId("test.ui.mapped"), "Mapped Action")
        val registry =
            object : JewelActionRegistry {
                override fun register(definition: JewelActionDefinition) = error("unused")

                override fun definition(id: JewelActionId): JewelActionDefinition? =
                    if (id == mapped.id) JewelActionDefinition(mapped) else null

                override fun definitions(): List<JewelActionDefinition> = listOf(JewelActionDefinition(mapped))

                override fun sampledPresentation(id: JewelActionId): ActionPresentation? =
                    if (id == mapped.id) {
                        ActionPresentation(
                            text = "Platform Disabled",
                            enabled = false,
                            resolution = ActionResolution.Resolved,
                        )
                    } else {
                        null
                    }
            }
        val localHost = JewelShortcutHostState(registry) { InMemoryJewelKeymap("empty") }
        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(Modifier.shortcut(mapped) {}.focusRequester(boundFocus).focusable())
            }
        }
        focus(boundFocus)

        rule.runOnIdle {
            val sampled = localHost.presentationFor(mapped.id)
            assertEquals(ActionResolution.Resolved, sampled.resolution)
            assertEquals("Platform Disabled", sampled.text)
            assertFalse("host-sampled disabled state must not be forced to enabled", sampled.enabled)
        }
    }

    @Test
    fun `claims, keymap bindings, and menu scopes authored with Key NumPadEnter fire on both numpad and main Enter`() {
        val submit = JewelAction(JewelActionId("test.ui.numpadAuthor"), "Submit")
        val localKeymap =
            InMemoryJewelKeymap("numpad-authored").apply {
                bind(submit.id, JewelKeySequence(JewelKeyStroke(Key.NumPadEnter, ctrl = true)))
            }
        val localHost = JewelShortcutHostState { localKeymap }
        var claimEnabled by mutableStateOf(true)
        var claimHits = 0
        var commandHits = 0
        var menuHits = 0

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(
                    Modifier.shortcut(submit) { commandHits++ }
                        .claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.NumPadEnter, ctrl = true)),
                            enabled = claimEnabled,
                        ) {
                            claimHits++
                        }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)

        val numpadEnterDown =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_ENTER,
                    '\n',
                    AwtKeyEvent.KEY_LOCATION_NUMPAD,
                )
                .toComposeKeyEvent()
        val mainEnterDown =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_ENTER,
                    '\n',
                    AwtKeyEvent.KEY_LOCATION_STANDARD,
                )
                .toComposeKeyEvent()

        rule.runOnIdle {
            // Claim authored with Key.NumPadEnter fires on both physical numpad Enter and main Enter.
            assertTrue(localHost.onPreviewKeyEvent(numpadEnterDown))
            assertTrue(localHost.onPreviewKeyEvent(mainEnterDown))
            assertEquals(2, claimHits)
            assertEquals(0, commandHits)

            // Disable claim -> keymap binding authored with Key.NumPadEnter fires on both Enter variants.
            claimEnabled = false
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue(localHost.onPreviewKeyEvent(numpadEnterDown))
            assertTrue(localHost.onPreviewKeyEvent(mainEnterDown))
            assertEquals(2, commandHits)

            // MenuShortcutScope authored with Key.NumPadEnter also matches physical numpad Enter.
            val menuScope = localHost.openMenuShortcutScope()
            try {
                menuScope.register(JewelKeyStroke(Key.NumPadEnter, ctrl = true)) { menuHits++ }
                assertTrue(localHost.onPreviewKeyEvent(numpadEnterDown))
                assertEquals(1, menuHits)
            } finally {
                menuScope.close()
            }
        }
    }

    @Test
    fun `closing registry registration rejects sampling, default invoker, and keymap dispatch as Unregistered`() {
        val action = JewelAction(JewelActionId("test.ui.regLifecycle"), "Lifecycle Action")
        val registry = DefaultJewelActionRegistry()
        val registration = registry.register(JewelActionDefinition(action))
        val keymap =
            InMemoryJewelKeymap("reg-lifecycle").apply {
                bind(action.id, JewelKeySequence(JewelKeyStroke(Key.R, ctrl = true)))
            }
        val localHost = JewelShortcutHostState(registry) { keymap }
        var runs = 0

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(Modifier.shortcut(action) { runs++ }.focusRequester(boundFocus).focusable())
            }
        }
        focus(boundFocus)

        val ctrlR =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_R,
                    AwtKeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()

        rule.runOnIdle {
            assertEquals(ActionResolution.Resolved, localHost.presentationFor(action.id).resolution)
            assertEquals(ActionDispatchResult.Dispatched, localHost.invoker.invoke(action))
            assertTrue(localHost.onPreviewKeyEvent(ctrlR))
            assertEquals(2, runs)

            registration.close()
            val afterClose = localHost.presentationFor(action.id)
            assertEquals(ActionResolution.Unregistered, afterClose.resolution)
            assertFalse(afterClose.enabled)
            assertEquals(
                ActionDispatchResult.Rejected(ActionDispatchRejection.Unregistered),
                localHost.invoker.invoke(action),
            )
            assertFalse("unregistered action must not dispatch from keymap", localHost.onPreviewKeyEvent(ctrlR))
            assertEquals(2, runs)
        }
    }

    @Test
    fun `moving resolverRootModifier to a new host invalidates the old host presentations`() {
        val action = JewelAction(JewelActionId("test.ui.hostSwapInvalidate"), "Swap Action")
        val hostA = JewelShortcutHostState { InMemoryJewelKeymap("a") }
        val hostB = JewelShortcutHostState { InMemoryJewelKeymap("b") }
        var activeHost by mutableStateOf(hostA)

        rule.setContent {
            Box(activeHost.resolverRootModifier) {
                Box(Modifier.shortcut(action) {}.focusRequester(boundFocus).focusable())
            }
        }
        focus(boundFocus)

        val flowA = hostA.presentations.acquire(action.id)
        val flowB = hostB.presentations.acquire(action.id)
        rule.runOnIdle {
            assertEquals(ActionResolution.Resolved, flowA.value.resolution)
            assertEquals(ActionResolution.NoFocusedBinding, flowB.value.resolution)
            activeHost = hostB
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(
                "detaching root from old host must invalidate its presentation flow",
                ActionResolution.NoFocusedBinding,
                flowA.value.resolution,
            )
            assertEquals(ActionResolution.Resolved, flowB.value.resolution)
        }
    }

    @Test
    fun `rememberJewelShortcutHostState reacts to new registry and keymap lambda parameters`() {
        val action = JewelAction(JewelActionId("test.ui.rememberHost"), "Remembered")
        val reg1 = DefaultJewelActionRegistry().apply { register(JewelActionDefinition(action)) }
        val reg2 = DefaultJewelActionRegistry()
        val keymap1 = InMemoryJewelKeymap("km1").apply { bind(action.id, JewelKeySequence(JewelKeyStroke(Key.F1))) }
        val keymap2 = InMemoryJewelKeymap("km2").apply { bind(action.id, JewelKeySequence(JewelKeyStroke(Key.F2))) }
        var activeRegistry by mutableStateOf<JewelActionRegistry>(reg1)
        var useSecondKeymap by mutableStateOf(false)
        lateinit var observedHost: JewelShortcutHostState

        rule.setContent {
            val provider: () -> JewelKeymap = if (useSecondKeymap) ({ keymap2 }) else ({ keymap1 })
            observedHost = rememberJewelShortcutHostState(activeRegistry, provider)
        }
        rule.runOnIdle {
            assertEquals(listOf(JewelKeySequence(JewelKeyStroke(Key.F1))), observedHost.shortcutsFor(action.id))
            assertEquals(ActionResolution.NoFocusedBinding, observedHost.presentationFor(action.id).resolution)
            useSecondKeymap = true
        }
        rule.waitForIdle()
        val hostAfterKeymapSwap = observedHost
        rule.runOnIdle {
            assertEquals(listOf(JewelKeySequence(JewelKeyStroke(Key.F2))), observedHost.shortcutsFor(action.id))
            activeRegistry = reg2
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue("changing registry must create a new host", observedHost !== hostAfterKeymapSwap)
            assertEquals(ActionResolution.Unregistered, observedHost.presentationFor(action.id).resolution)
        }
    }

    @Test
    fun `collectPresentationAsState updates immediately on selector switch and skips recomposition on unused field`() {
        val action = JewelAction(JewelActionId("test.ui.selectorSwitch"), "Initial Text")
        var currentSample =
            ActionPresentation(
                text = "Initial Text",
                description = "Initial Desc",
                enabled = true,
                resolution = ActionResolution.Resolved,
            )
        val scheduler = ActionPresentationScheduler { currentSample }
        var useDescription by mutableStateOf(false)
        var observedValue: String? = null
        var readerRecompositions = 0

        @Composable
        fun SelectorObserver(selector: (ActionPresentation) -> String?) {
            val projectedState = action.collectPresentationAsState(scheduler, selector)
            // Read projectedState inside an inner composable so outer recompositions from switching selector are
            // separated from reader recompositions caused by presentation updates.
            Box {
                SideEffect { readerRecompositions++ }
                observedValue = projectedState.value
            }
        }

        rule.setContent {
            val selector: (ActionPresentation) -> String? = if (useDescription) ({ it.description }) else ({ it.text })
            SelectorObserver(selector)
        }
        rule.runOnIdle {
            assertEquals("Initial Text", observedValue)
            val beforeUnusedUpdate = readerRecompositions
            // Changing an unobserved field (description while observing text) must trigger 0 recompositions.
            currentSample = currentSample.copy(description = "Updated Desc")
            scheduler.invalidate(action.id)
            assertEquals(beforeUnusedUpdate, readerRecompositions)
        }
        rule.waitForIdle()
        val beforeSwitch = readerRecompositions
        rule.runOnIdle {
            assertEquals(beforeSwitch, readerRecompositions)
            useDescription = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(
                "switching selector must immediately project the new field without a new emission",
                "Updated Desc",
                observedValue,
            )
        }
    }

    @Test
    fun `DefaultJewelActionRegistry invalidates acquired presentation flows on close and re-register`() {
        val action = JewelAction(JewelActionId("test.ui.liveRegFlow"), "Live Reg")
        val registry = DefaultJewelActionRegistry()
        val reg1 = registry.register(JewelActionDefinition(action))
        val localHost = JewelShortcutHostState(registry) { InMemoryJewelKeymap("live-reg") }

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(Modifier.shortcut(action) {}.focusRequester(boundFocus).focusable())
            }
        }
        focus(boundFocus)

        val flow = localHost.presentations.acquire(action.id)
        rule.runOnIdle {
            assertEquals(ActionResolution.Resolved, flow.value.resolution)
            assertTrue(flow.value.enabled)

            reg1.close()
            assertEquals(
                "closing last registration must invalidate acquired flow to Unregistered",
                ActionResolution.Unregistered,
                flow.value.resolution,
            )
            assertFalse(flow.value.enabled)

            val reg2 = registry.register(JewelActionDefinition(action))
            try {
                assertEquals(
                    "re-registering must invalidate acquired flow back to Resolved",
                    ActionResolution.Resolved,
                    flow.value.resolution,
                )
                assertTrue(flow.value.enabled)
            } finally {
                reg2.close()
            }
        }
    }

    @Test
    fun `unregistered sampledPresentation does not bypass registration checks and disabled bindings report Disabled`() {
        val action = JewelAction(JewelActionId("test.ui.unregSample"), "Unregistered Sample")
        val fakeUnregisteredRegistry =
            object : JewelActionRegistry {
                override fun register(definition: JewelActionDefinition) = error("unused")

                override fun definition(id: JewelActionId): JewelActionDefinition? = null

                override fun definitions(): List<JewelActionDefinition> = emptyList()

                override fun sampledPresentation(id: JewelActionId): ActionPresentation =
                    ActionPresentation(text = id.value, enabled = false, resolution = ActionResolution.Unregistered)
            }
        val unregHost = JewelShortcutHostState(fakeUnregisteredRegistry) { InMemoryJewelKeymap("unreg") }
        val bindingEnabled = false
        val disabledAction = JewelAction(JewelActionId("test.ui.disabledInvoke"), "Disabled Invoke")
        val normalHost = JewelShortcutHostState { InMemoryJewelKeymap("normal") }

        rule.setContent {
            Box(unregHost.resolverRootModifier) {
                Box(normalHost.resolverRootModifier) {
                    Box(
                        Modifier.shortcut(action) {}
                            .shortcut(disabledAction, enabled = bindingEnabled) {}
                            .focusRequester(boundFocus)
                            .focusable()
                    )
                }
            }
        }
        focus(boundFocus)

        rule.runOnIdle {
            val sampled = unregHost.presentationFor(action.id)
            assertEquals(ActionResolution.Unregistered, sampled.resolution)
            assertFalse(sampled.enabled)
            assertEquals(
                ActionDispatchResult.Rejected(ActionDispatchRejection.Unregistered),
                unregHost.invoker.invoke(action),
            )
            assertEquals(
                "focused disabled binding must reject as Disabled rather than NoFocusedBinding",
                ActionDispatchResult.Rejected(ActionDispatchRejection.Disabled),
                normalHost.invoker.invoke(disabledAction),
            )
            assertEquals(
                ActionDispatchResult.Rejected(ActionDispatchRejection.NoFocusedBinding),
                normalHost.invoker.invoke(JewelAction(JewelActionId("test.ui.unbound"), "Unbound")),
            )
        }
    }

    @Test
    fun `raw claim consumption notifies onDispatch, emits lifecycle event, and invalidates presentations`() {
        val observedAction = JewelAction(JewelActionId("test.ui.rawObserve"), "Observed")
        var sampleText = "Before Raw"
        val registry =
            object : JewelActionRegistry {
                override fun register(definition: JewelActionDefinition) = error("unused")

                override fun definition(id: JewelActionId) = JewelActionDefinition(observedAction)

                override fun definitions() = listOf(JewelActionDefinition(observedAction))

                override fun sampledPresentation(id: JewelActionId) =
                    ActionPresentation(text = sampleText, enabled = true, resolution = ActionResolution.Resolved)
            }
        val localHost = JewelShortcutHostState(registry) { InMemoryJewelKeymap("raw") }
        val decisions = mutableListOf<DispatchDecision.Consumed>()
        localHost.onDispatch = { decisions += it }
        val invocations = mutableListOf<ActionInvocation>()

        @Suppress("InjectDispatcher") val scope = CoroutineScope(Dispatchers.Unconfined)
        localHost.events.invocations.onEach { invocations += it }.launchIn(scope)

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(
                    Modifier.claimKeyEvent(matcher = { it.key == Key.H && it.isCtrlPressed }) {
                            sampleText = "After Raw"
                        }
                        .focusRequester(claimerFocus)
                        .focusable()
                )
            }
        }
        focus(claimerFocus)

        val flow = localHost.presentations.acquire(observedAction.id)
        try {
            rule.runOnIdle {
                assertEquals("Before Raw", flow.value.text)
                val ctrlH =
                    AwtKeyEvent(
                            Panel(),
                            AwtKeyEvent.KEY_PRESSED,
                            0L,
                            InputEvent.CTRL_DOWN_MASK,
                            AwtKeyEvent.VK_H,
                            AwtKeyEvent.CHAR_UNDEFINED,
                        )
                        .toComposeKeyEvent()
                assertTrue(localHost.onPreviewKeyEvent(ctrlH))
                assertEquals(
                    listOf(DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.Claim)),
                    decisions,
                )
                assertEquals(listOf(ActionInvocation(null, null, ActionTrigger.Keyboard(null))), invocations)
                assertEquals("After Raw", flow.value.text)
            }
        } finally {
            scope.cancel()
        }
    }
}

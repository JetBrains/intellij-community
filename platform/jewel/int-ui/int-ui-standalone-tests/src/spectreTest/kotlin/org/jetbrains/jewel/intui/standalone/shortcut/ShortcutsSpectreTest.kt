// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.shortcut.DefaultJewelActionRegistry
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.jetbrains.jewel.intui.standalone.awaitWindow
import org.jetbrains.jewel.intui.standalone.stopApplicationWindow
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.junit.jupiter.api.Test

/**
 * Headful standalone Spectre test for keymap dispatch: nested binding override, disabled fall-through,
 * `blocksOuterBindings`, two-stroke chord pending/completion/nonmatching cancel, and focus-loss reset.
 */
class ShortcutsSpectreTest {
    @Test
    fun `standalone keymap dispatch honors override, fall-through, blocking, chords, and focus-loss reset`(): Unit =
        runSpectreTest {
            val app = ShortcutsSpectreApp()
            app.start()
            try {
                val window = app.awaitWindow()
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(window))
                automator.waitForNode(tag = TAG_INNER, timeout = 10.seconds)
                automator.waitForIdle()

                suspend fun clickTag(tag: String) {
                    automator.refreshWindows()
                    val node = automator.findOneByTestTag(tag) ?: error("Missing '$tag'")
                    automator.click(node)
                    automator.waitForIdle()
                }

                fun textOf(tag: String): String {
                    automator.refreshWindows()
                    return automator.findOneByTestTag(tag)?.text.orEmpty()
                }

                // 1. Nested override wins while inner card is focused.
                clickTag(TAG_INNER)
                automator.pressKey(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("inner=1", textOf(TAG_INNER_COUNT))
                assertEquals("outer=0", textOf(TAG_OUTER_COUNT))

                // 2. Disabling inner falls through to outer.
                clickTag(TAG_INNER_ENABLED)
                clickTag(TAG_INNER)
                automator.pressKey(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("inner=1", textOf(TAG_INNER_COUNT))
                assertEquals("outer=1", textOf(TAG_OUTER_COUNT))

                // 3. blocksOuterBindings stops fall-through when inner is disabled.
                clickTag(TAG_INNER_BLOCKS)
                clickTag(TAG_INNER)
                automator.pressKey(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("inner=1", textOf(TAG_INNER_COUNT))
                assertEquals("outer=1", textOf(TAG_OUTER_COUNT))

                // 4. Two-stroke chord: pending -> completion -> nonmatching cancel.
                clickTag(TAG_OUTER_FOCUS)
                automator.pressKey(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("pending=true", textOf(TAG_CHORD_PENDING))
                assertTrue(app.hostState.isAwaitingSecondStroke)

                automator.pressKey(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("chord=1", textOf(TAG_CHORD_COUNT))
                assertFalse(app.hostState.isAwaitingSecondStroke)

                // Arm first stroke, then send nonmatching stroke (Ctrl+X): cancels chord without firing.
                automator.pressKey(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertTrue(app.hostState.isAwaitingSecondStroke)
                automator.pressKey(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertFalse(app.hostState.isAwaitingSecondStroke)
                assertEquals("chord=1", textOf(TAG_CHORD_COUNT))

                // 5. Focus-loss reset: arm first stroke, move focus outside resolver root, move back, second stroke
                // alone must not complete the chord; re-arming and completing must work.
                automator.pressKey(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertTrue(app.hostState.isAwaitingSecondStroke)

                clickTag(TAG_OUTSIDE_ROOT)
                assertFalse(app.hostState.isAwaitingSecondStroke)

                clickTag(TAG_OUTER_FOCUS)
                automator.pressKey(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("chord=1", textOf(TAG_CHORD_COUNT))

                automator.pressKey(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
                automator.waitForIdle()
                assertEquals("chord=2", textOf(TAG_CHORD_COUNT))
            } finally {
                app.stop()
            }
        }
}

private class ShortcutsSpectreApp {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)
    private val appThread = AtomicReference<Thread?>(null)

    private val selectAllAction = JewelAction(JewelActionId("test.spectre.selectAll"), "Select All")
    private val chordAction = JewelAction(JewelActionId("test.spectre.chord"), "Reformat Chord")

    private val registry =
        DefaultJewelActionRegistry().apply {
            register(
                JewelActionDefinition(selectAllAction, listOf(JewelKeySequence(JewelKeyStroke(Key.A, ctrl = true))))
            )
            register(
                JewelActionDefinition(
                    chordAction,
                    listOf(JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true))),
                )
            )
        }

    private val keymap = InMemoryJewelKeymap.fromDefaults("spectre-shortcuts", registry)
    val hostState = JewelShortcutHostState(registry) { keymap }

    fun start() {
        val startedThread =
            thread(name = "spectre-shortcuts-window", isDaemon = true) {
                application(exitProcessOnExit = false) {
                    exitApplication.set(::exitApplication)
                    JewelWindow(
                        onCloseRequest = ::exitApplication,
                        title = "Shortcuts Spectre",
                        onPreviewKeyEvent = hostState::onPreviewKeyEvent,
                    ) {
                        this@ShortcutsSpectreApp.window.compareAndSet(null, window)
                        IntUiTheme { ShortcutsSpectreContent(hostState, selectAllAction, chordAction) }
                    }
                }
            }
        appThread.set(startedThread)
    }

    fun stop() {
        stopApplicationWindow(exitApplication, window, appThread)
    }

    suspend fun awaitWindow(): ComposeWindow = window.awaitWindow()
}

@Composable
private fun ShortcutsSpectreContent(
    hostState: JewelShortcutHostState,
    selectAllAction: JewelAction,
    chordAction: JewelAction,
) {
    var outerCount by remember { mutableIntStateOf(0) }
    var innerCount by remember { mutableIntStateOf(0) }
    var chordCount by remember { mutableIntStateOf(0) }
    var chordPending by remember { mutableStateOf(false) }
    var innerEnabled by remember { mutableStateOf(true) }
    var innerBlocksOuter by remember { mutableStateOf(false) }

    DisposableEffect(hostState) {
        hostState.onDispatch = { chordPending = hostState.isAwaitingSecondStroke }
        onDispose { hostState.onDispatch = null }
    }

    val outerFocus = remember { FocusRequester() }
    val innerFocus = remember { FocusRequester() }
    val outsideFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.testTag(TAG_OUTSIDE_ROOT).clickToFocus(outsideFocus).focusable()) { Text("Outside resolver root") }

        ProvideJewelShortcutHost(hostState) {
            Column(
                hostState.resolverRootModifier
                    .shortcut(selectAllAction) { outerCount++ }
                    .shortcut(chordAction) { chordCount++ },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.testTag(TAG_OUTER_FOCUS).clickToFocus(outerFocus).focusable()) {
                    Text("Outer focus target")
                }
                Text("outer=$outerCount", modifier = Modifier.testTag(TAG_OUTER_COUNT))
                Text("chord=$chordCount", modifier = Modifier.testTag(TAG_CHORD_COUNT))
                Text("pending=$chordPending", modifier = Modifier.testTag(TAG_CHORD_PENDING))

                Box(
                    Modifier.testTag(TAG_INNER)
                        .clickToFocus(innerFocus)
                        .shortcut(selectAllAction, enabled = innerEnabled, blocksOuterBindings = innerBlocksOuter) {
                            innerCount++
                        }
                        .focusable()
                ) {
                    Text("inner=$innerCount", modifier = Modifier.testTag(TAG_INNER_COUNT))
                }

                CheckboxRow(
                    text = "Inner enabled",
                    checked = innerEnabled,
                    onCheckedChange = { innerEnabled = it },
                    modifier = Modifier.testTag(TAG_INNER_ENABLED),
                )
                CheckboxRow(
                    text = "Inner blocks outer",
                    checked = innerBlocksOuter,
                    onCheckedChange = { innerBlocksOuter = it },
                    modifier = Modifier.testTag(TAG_INNER_BLOCKS),
                )
            }
        }
    }
}

private fun Modifier.clickToFocus(focusRequester: FocusRequester): Modifier =
    focusRequester(focusRequester).pointerInput(focusRequester) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            focusRequester.requestFocus()
            down.consume()
        }
    }

private const val TAG_OUTSIDE_ROOT = "shortcuts.outsideRoot"
private const val TAG_OUTER_FOCUS = "shortcuts.outerFocus"
private const val TAG_OUTER_COUNT = "shortcuts.outerCount"
private const val TAG_INNER = "shortcuts.inner"
private const val TAG_INNER_COUNT = "shortcuts.innerCount"
private const val TAG_INNER_ENABLED = "shortcuts.innerEnabled"
private const val TAG_INNER_BLOCKS = "shortcuts.innerBlocks"
private const val TAG_CHORD_COUNT = "shortcuts.chordCount"
private const val TAG_CHORD_PENDING = "shortcuts.chordPending"

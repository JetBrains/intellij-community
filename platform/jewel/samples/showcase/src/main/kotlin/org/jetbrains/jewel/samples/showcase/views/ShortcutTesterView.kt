// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.shortcut.ActionInvocation
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.LocalJewelShortcutHost
import org.jetbrains.jewel.foundation.shortcut.claimShortcut
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts.DemoPing
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts.DemoReformat
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts.DemoSelectAll
import org.jetbrains.jewel.samples.showcase.ShowcaseTestTags
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * Interactive tester for the shortcut subsystem. Each rule is a section of focusable cards whose border lights up when
 * focused, so the nearest-focused-binding, claim-veto, typed-suppression, and two-stroke-chord behaviors are legible at
 * a glance: the invocation log floats in the right rail and records one entry per completed Jewel-owned invocation.
 *
 * Requires a `LocalJewelShortcutHost`; the standalone sample installs one at the window root, the IDE bridge provides
 * one per panel automatically.
 */
@Composable
public fun ShortcutTesterView(modifier: Modifier = Modifier) {
    val host = LocalJewelShortcutHost.current
    if (host == null) {
        Text("No LocalJewelShortcutHost is installed — the tester needs a shortcut host.")
        return
    }

    // Live event log: exactly one entry per completed Jewel-owned invocation.
    val events = remember { mutableStateListOf<String>() }
    LaunchedEffect(host) {
        host.events.invocations.collect { invocation ->
            events.add(0, invocation.describe())
            if (events.size > 50) events.removeAt(events.lastIndex)
        }
    }

    var outerSelectAll by remember { mutableIntStateOf(0) }
    var editorSelectAll by remember { mutableIntStateOf(0) }
    var editorOverrideEnabled by remember { mutableStateOf(true) }
    var editorBlocksOuter by remember { mutableStateOf(false) }
    var outerFocused by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val outerFocus = remember { FocusRequester() }
    val editorFocus = remember { FocusRequester() }

    var pings by remember { mutableIntStateOf(0) }
    var claimVetoes by remember { mutableIntStateOf(0) }
    var claimActive by remember { mutableStateOf(true) }
    var pingFocused by remember { mutableStateOf(false) }
    var claimerFocused by remember { mutableStateOf(false) }
    val pingFocus = remember { FocusRequester() }
    val claimerFocus = remember { FocusRequester() }

    var macroRecordings by remember { mutableIntStateOf(0) }

    var reformats by remember { mutableIntStateOf(0) }
    var reformatFocused by remember { mutableStateOf(false) }
    val reformatFocus = remember { FocusRequester() }

    Row(
        modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoText(
                "Shortcuts route through the host keymap; claims own keys ahead of it. Click a card to focus it " +
                    "(its border lights up), then use the keystrokes the section names."
            )

            VerticallyScrollableContainer(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ----- 1. Claims veto keymap commands -----
                    Section("Claims veto commands — ${DemoPing.shortcutHint(host)}") {
                        Column(
                            Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_PING)
                                .focusCard(pingFocus, pingFocused)
                                .shortcut(DemoPing) { pings++ }
                                .onFocusChanged { pingFocused = it.isFocused }
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Ping action — fired $pings times",
                                modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_PING_COUNTER),
                            )
                            Column(
                                Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_CLAIMER)
                                    .focusCard(claimerFocus, claimerFocused)
                                    .claimShortcut(
                                        // Same physical stroke DemoPing is bound to (Alt+G on every OS).
                                        sequence = JewelKeySequence(JewelKeyStroke(Key.G, alt = true)),
                                        enabled = claimActive,
                                    ) {
                                        claimVetoes++
                                    }
                                    .onFocusChanged { claimerFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Text(
                                    "Claiming surface — claimed $claimVetoes times",
                                    modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_CLAIMER_COUNTER),
                                )
                            }
                            CheckboxRow(
                                text = "Claim active while focused",
                                checked = claimActive,
                                onCheckedChange = { claimActive = it },
                                modifier =
                                    Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_CLAIM_ACTIVE).semantics {
                                        selected = claimActive
                                    },
                            )
                        }
                    }

                    // ----- 2. Typed suppression -----
                    Section("Typed suppression — claimed printable K") {
                        val fieldState = rememberTextFieldState()
                        TextField(
                            state = fieldState,
                            modifier =
                                Modifier.width(320.dp)
                                    .testTag(ShowcaseTestTags.SHORTCUT_TESTER_TEXT_FIELD)
                                    .claimShortcut(sequence = JewelKeySequence(JewelKeyStroke(Key.K))) {
                                        macroRecordings++
                                    },
                            placeholder = { Text("Type here: K runs the macro and is suppressed; other keys type") },
                        )
                        Text(
                            "Macro recorded $macroRecordings times; a claimed printable key never types its character.",
                            modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_MACRO_COUNTER),
                        )
                    }

                    // ----- 3. Nested bindings: nearest focused enabled binding wins -----
                    Section("Nested bindings — ${DemoSelectAll.shortcutHint(host)}") {
                        Column(
                            Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_OUTER)
                                .focusCard(outerFocus, outerFocused)
                                .shortcut(DemoSelectAll) { outerSelectAll++ }
                                .onFocusChanged { outerFocused = it.isFocused }
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Outer surface binding — fired $outerSelectAll times",
                                modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_OUTER_COUNTER),
                            )
                            Column(
                                Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_EDITOR)
                                    .focusCard(editorFocus, editorFocused)
                                    .shortcut(
                                        DemoSelectAll,
                                        enabled = editorOverrideEnabled,
                                        blocksOuterBindings = editorBlocksOuter,
                                    ) {
                                        editorSelectAll++
                                    }
                                    .onFocusChanged { editorFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Text(
                                    "Inner 'editor' override — fired $editorSelectAll times",
                                    modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_EDITOR_COUNTER),
                                )
                            }
                            CheckboxRow(
                                text = "Inner override enabled (disabled falls through to the outer binding)",
                                checked = editorOverrideEnabled,
                                onCheckedChange = { editorOverrideEnabled = it },
                            )
                            CheckboxRow(
                                text = "Disabled inner override blocks the outer binding instead of falling through",
                                checked = editorBlocksOuter,
                                onCheckedChange = { editorBlocksOuter = it },
                            )
                        }
                    }

                    // ----- 4. Two-stroke chord -----
                    Section("Two-stroke chord — ${DemoReformat.shortcutHint(host)}") {
                        Column(
                            Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_REFORMAT)
                                .focusCard(reformatFocus, reformatFocused)
                                .shortcut(DemoReformat) { reformats++ }
                                .onFocusChanged { reformatFocused = it.isFocused }
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Reformat — fired $reformats times")
                            Text(
                                if (host.isAwaitingSecondStroke) "… awaiting second stroke" else "chord idle",
                                modifier = Modifier.testTag(ShowcaseTestTags.SHORTCUT_TESTER_REFORMAT_STATUS),
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader("Invocations")
            VerticallyScrollableContainer(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (events.isEmpty()) InfoText("Nothing invoked yet.")
                    for (line in events) Text(line)
                }
            }
        }
    }
}

private fun ActionInvocation.describe(): String {
    val trigger =
        when (val t = trigger) {
            is ActionTrigger.Keyboard -> "keyboard ${t.sequence?.displayText().orEmpty()}".trim()
            is ActionTrigger.Pointer -> "pointer"
            is ActionTrigger.Programmatic -> "programmatic"
        }
    val id = actionId?.value?.substringAfterLast('.') ?: sequence?.displayText() ?: "claim"
    return "$id — $trigger"
}

@Composable
private fun JewelAction.shortcutHint(host: JewelShortcutHostState): String =
    host.shortcutsFor(id).firstOrNull()?.displayText() ?: "unbound"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader(title)
        content()
    }
}

/**
 * A focusable demo surface: rounded, bordered, and highlighted with the focus outline color while focused. A plain
 * [focusable] is not focused by a pointer click, so this also requests focus on any press its children did not consume.
 */
@Composable
private fun Modifier.focusCard(focusRequester: FocusRequester, focused: Boolean): Modifier =
    fillMaxWidth()
        .focusRequester(focusRequester)
        .pointerInput(focusRequester) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                focusRequester.requestFocus()
                down.consume()
            }
        }
        .border(
            width = 1.dp,
            color = if (focused) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.borders.normal,
            shape = RoundedCornerShape(4.dp),
        )
        .padding(12.dp)

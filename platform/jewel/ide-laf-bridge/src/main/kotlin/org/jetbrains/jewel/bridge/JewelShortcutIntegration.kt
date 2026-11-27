// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.intellij.ide.ActivityTracker
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.SwingUtilities
import org.jetbrains.jewel.bridge.actionSystem.JewelActionMappings
import org.jetbrains.jewel.bridge.actionSystem.JewelBridgeActionInvoker
import org.jetbrains.jewel.bridge.actionSystem.JewelBridgeActionRegistry
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
import org.jetbrains.jewel.foundation.shortcut.toComposeKeyEvent

/**
 * Wires the Jewel shortcut claim lane into a bridge-hosted Compose panel.
 *
 * Two cooperating halves:
 * - **Veto**: [JewelComposePanelWrapper] implements `KeyboardAwareContainer`; when a focused
 *   `Modifier.claimShortcut`/`claimKeyEvent` node owns a key-down, `IdeKeyEventDispatcher` skips IDE keymap processing.
 *   The wrapper — not the focus owner — answers, because the actual AWT focus owner inside a `ComposePanel` is an
 *   internal skiko component the embedder cannot control.
 * - **Delivery**: a [KeyEventDispatcher] scoped to this wrapper's focused descendants consumes the claimed key-down,
 *   invokes the claim handler, and swallows the trailing KEY_TYPED so claimed printable keys do not leak characters
 *   into focused text fields. It runs at the KeyboardFocusManager stage — after the IDE keymap has already declined the
 *   event via the veto — so no Compose Multiplatform hook is required.
 *
 * Commands (`Modifier.shortcut`) are intentionally NOT dispatched here: in the IJPL host they remain platform actions
 * resolved through the IDE keymap, so the bridge host state uses an empty Jewel keymap. It does carry a
 * [JewelBridgeActionRegistry], though, so controls bound to a command still resolve that action's template presentation
 * from the platform.
 */
@Composable
internal fun ShortcutHostBridge(wrapper: JewelComposePanelWrapper, content: @Composable () -> Unit) {
    val bridgeKeymap = remember { InMemoryJewelKeymap("jewel-bridge-claims-only") }
    // The host owns a bridge registry so that actions the IDE already declares resolve their template
    // presentation — text, description and icon — for any control bound to them. Without one, controls
    // rendered inside a bridge panel would fall back to the binding's origin text and no icon; supplying a
    // registry by nesting a second host is not an alternative, because the platform side resolves handlers
    // through the host this wrapper publishes, and bindings under a nested host are invisible to it.
    val registry = remember(wrapper) { JewelBridgeActionRegistry(hostComponent = wrapper) }
    val state =
        remember(wrapper, registry) {
            // Idempotent and non-clobbering; running before initial composition guarantees standard edit actions
            // resolve on Frame 1 before any control samples them.
            JewelActionMappings.installStandardMappings()
            JewelShortcutHostState(registry) { bridgeKeymap }
                .apply {
                    // Route programmatic invocations through the platform action system, so ActionManager update,
                    // enablement, listeners, and dumb-mode handling stay authoritative.
                    invoker = JewelBridgeActionInvoker(wrapper)
                    registry.onAsyncSampleUpdated = { actionId ->
                        registry.withCachedOnlyRead { presentations.invalidate(actionId) }
                    }
                }
        }

    DisposableEffect(wrapper, state) {
        registry.onAsyncSampleUpdated = { actionId ->
            registry.withCachedOnlyRead { state.presentations.invalidate(actionId) }
        }
        val removeClaimLane = installBridgeClaimLane(wrapper, state)

        // Presentation sampling rides the platform's action-update cadence: the same timer that refreshes
        // toolbars re-samples Jewel presentations, but only when user activity actually advanced and only
        // for actions some composed control is observing (the scheduler is demand-driven).
        val actionManager = ActionManager.getInstance()
        var lastActivityCount = -1
        val presentationTimerListener =
            object : TimerListener {
                override fun getModalityState(): ModalityState = ModalityState.any()

                override fun run() {
                    if (state.presentations.activeDemandCount() == 0) return
                    val count = ActivityTracker.getInstance().count
                    if (count != lastActivityCount) {
                        lastActivityCount = count
                        state.presentations.invalidate()
                    }
                }
            }
        actionManager.addTimerListener(presentationTimerListener)

        onDispose {
            registry.onAsyncSampleUpdated = null
            registry.cancelPendingSamples()
            actionManager.removeTimerListener(presentationTimerListener)
            removeClaimLane()
            state.reset()
        }
    }

    ProvideJewelShortcutHost(state) { Box(state.resolverRootModifier) { content() } }
}

/**
 * Installs `KeyboardAwareContainer` claim evaluation and wrapper-scoped `KeyEventDispatcher` delivery (forwarding
 * `KEY_PRESSED`, `KEY_RELEASED`, and `KEY_TYPED`) for [wrapper] against [state], returning a cleanup callback.
 */
internal fun installBridgeClaimLane(wrapper: JewelComposePanelWrapper, state: JewelShortcutHostState): () -> Unit {
    wrapper.shortcutHostState = state
    wrapper.shortcutClaimEvaluator = { awtEvent ->
        if (awtEvent.id != AwtKeyEvent.KEY_PRESSED) {
            false
        } else {
            val composeEvent = awtEvent.toComposeKeyEvent()
            val claimed = state.claimsKeyDown(composeEvent)
            if (!claimed) {
                state.retireStaleTypedSuppressionForUnclaimedKeyDown(composeEvent)
            }
            claimed
        }
    }

    // Platform $SelectAll (SelectAllAction) extends TextComponentEditorAction and has no SelectAllProvider hook.
    // A panel-scoped DumbAware action following $SelectAll's live active-keymap shortcuts lets
    // Modifier.shortcut(JewelActions.SelectAll) run on custom Jewel widgets (lists, tables) — including during
    // indexing — while returning an empty ShortcutSet whenever no SelectAll binding is focused so plain TextFields
    // keep Ctrl/Cmd+A and IdeKeyEventDispatcher continues walking up Swing ancestors for parent component shortcuts.
    val selectAllAction = JewelSelectAllBridgeAction(wrapper, state)
    val selectAllShortcutSet = ShortcutSet {
        if (
            ApplicationManager.getApplication() == null ||
                JewelActionMappings.ideActionIdFor(JewelActions.SelectAll.id) != IdeActions.ACTION_SELECT_ALL ||
                resolveSelectAllHandler(wrapper, state) == null
        ) {
            Shortcut.EMPTY_ARRAY
        } else {
            KeymapManager.getInstance()?.activeKeymap?.getShortcuts(IdeActions.ACTION_SELECT_ALL)
                ?: Shortcut.EMPTY_ARRAY
        }
    }
    selectAllAction.registerCustomShortcutSet(selectAllShortcutSet, wrapper)
    wrapper.selectAllBridgeAction = selectAllAction

    val app = ApplicationManager.getApplication()
    val laneDisposable =
        if (app != null && !app.isDisposed) {
            Disposer.newDisposable("JewelBridgeClaimLane").also { disposable ->
                // Observe KEY_RELEASED ahead of IdePopupManager / IdeKeyEventDispatcher so OnceUntilRelease latches
                // still re-arm even if a popup or earlier IdeEventQueue preprocessor intercepts the release before
                // KeyboardFocusManager runs.
                IdeEventQueue.getInstance()
                    .addPreprocessor(
                        IdeEventQueue.EventDispatcher { awtEvent ->
                            if (awtEvent is AwtKeyEvent && awtEvent.id == AwtKeyEvent.KEY_RELEASED) {
                                state.onPreviewKeyEvent(awtEvent.toComposeKeyEvent())
                            }
                            false
                        },
                        disposable,
                    )
            }
        } else {
            null
        }

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val claimDeliveryDispatcher = KeyEventDispatcher { event ->
        // Always forward KEY_RELEASED (even when focus moved to a dialog or the IDE consumed an Alt release) so
        // OnceUntilRelease latches never get stuck; KeyUp only clears latches and never consumes.
        if (event.id == AwtKeyEvent.KEY_RELEASED) {
            state.onPreviewKeyEvent(event.toComposeKeyEvent())
            return@KeyEventDispatcher false
        }
        // Skip events already consumed by IdeKeyEventDispatcher (for example, the second stroke of an IDE chord
        // where KeyboardAwareContainer is not consulted) so a focused Jewel claim cannot double-fire.
        if (event.isConsumed) {
            return@KeyEventDispatcher false
        }
        val focusOwner = focusManager.focusOwner
        if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, wrapper)) {
            return@KeyEventDispatcher false
        }
        val consumed =
            when (event.id) {
                AwtKeyEvent.KEY_PRESSED,
                AwtKeyEvent.KEY_TYPED -> state.onPreviewKeyEvent(event.toComposeKeyEvent())
                else -> false
            }
        if (consumed) event.consume()
        consumed
    }
    focusManager.addKeyEventDispatcher(claimDeliveryDispatcher)

    return {
        laneDisposable?.let(Disposer::dispose)
        focusManager.removeKeyEventDispatcher(claimDeliveryDispatcher)
        selectAllAction.unregisterCustomShortcutSet(wrapper)
        wrapper.selectAllBridgeAction = null
        wrapper.shortcutClaimEvaluator = null
        wrapper.shortcutHostState = null
    }
}

private fun resolveSelectAllHandler(wrapper: JewelComposePanelWrapper, state: JewelShortcutHostState): (() -> Unit)? {
    val activePopup = wrapper.topActivePopupWrapper()
    val activeSurfacePanel = activePopup?.composePanel ?: wrapper.composePanel
    val allowCrossSurfaceFallback = activePopup == null && !wrapper.isPopupWrapper
    return state.resolveFocusedHandlerForComponent(
        actionId = JewelActions.SelectAll.id,
        targetComponent = activeSurfacePanel,
        allowCrossSurfaceFallback = allowCrossSurfaceFallback,
        excludedComponents = if (allowCrossSurfaceFallback) wrapper.activePopupPanels() else emptySet(),
    )
}

internal class JewelSelectAllBridgeAction(
    private val wrapper: JewelComposePanelWrapper,
    private val state: JewelShortcutHostState,
) : AnAction(), DumbAware {
    init {
        isEnabledInModalContext = true
        val text =
            if (ApplicationManager.getApplication() != null) {
                ActionManager.getInstance()?.getAction(IdeActions.ACTION_SELECT_ALL)?.templatePresentation?.text
            } else {
                null
            } ?: "Select All"
        templatePresentation.setText(text, false)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = resolveSelectAllHandler(wrapper, state) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val handler = resolveSelectAllHandler(wrapper, state) ?: return
        val keyEvent = event.inputEvent as? AwtKeyEvent
        if (
            keyEvent?.id == AwtKeyEvent.KEY_PRESSED &&
                state.consumeIfRepeatSuppressed(JewelActions.SelectAll.id, keyEvent.toComposeKeyEvent())
        ) {
            return
        }
        handler()
        state.presentations.invalidate()
    }
}

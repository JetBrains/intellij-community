// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchRejection
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchResult
import org.jetbrains.jewel.foundation.shortcut.ActionInvoker
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions

/**
 * Explicit routing of Jewel action IDs onto existing platform actions (the `MappedIdeAction` route). A mapped action
 * keeps its native `AnAction` class, `update()`, presentation, and `DataContext` semantics — the mapping only tells the
 * bridge invoker which platform action to execute. Mapped actions never emit Jewel lifecycle events; observe them
 * through ordinary IJPL facilities.
 *
 * Applications may override a default mapping before any invocation; [map] validates that the target platform action
 * exists.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object JewelActionMappings {
    private class MappingEntry(val ideActionId: String, val previous: MappingEntry?) {
        @Volatile var active: Boolean = true
    }

    private val mappings = ConcurrentHashMap<JewelActionId, MappingEntry>()
    private val standardDefaults = ConcurrentHashMap<JewelActionId, String>()

    /**
     * Maps [jewelActionId] to an existing platform action [ideActionId], validating with [ActionManager] that the
     * target exists, and returns a handle that restores the prior mapping on [AutoCloseable.close] if this override is
     * still active.
     */
    public fun map(jewelActionId: JewelActionId, ideActionId: String): AutoCloseable =
        map(jewelActionId, ideActionId, ActionManager.getInstance())

    internal fun map(jewelActionId: JewelActionId, ideActionId: String, actionManager: ActionManager): AutoCloseable {
        requireNotNull(actionManager.getAction(ideActionId)) {
            "Unknown platform action ID '$ideActionId' for Jewel action '${jewelActionId.value}'"
        }
        lateinit var installed: MappingEntry
        mappings.compute(jewelActionId) { _, existing ->
            MappingEntry(ideActionId = ideActionId, previous = existing).also { installed = it }
        }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (!closed.compareAndSet(false, true)) return@AutoCloseable
            mappings.compute(jewelActionId) { _, current ->
                installed.active = false
                if (current !== installed) {
                    current
                } else {
                    var candidate = installed.previous
                    while (candidate != null && !candidate.active) {
                        candidate = candidate.previous
                    }
                    candidate
                }
            }
        }
    }

    /** Returns the mapped platform action ID for [jewelActionId], or `null` when none is registered. */
    public fun ideActionIdFor(jewelActionId: JewelActionId): String? =
        mappings[jewelActionId]?.ideActionId ?: standardDefaults[jewelActionId]

    /**
     * Bridge defaults for the standard edit actions. Idempotent and non-clobbering: an explicit [map] override for a
     * standard action survives any number of install calls, so hosts may install eagerly (`ShortcutHostBridge` does, on
     * every panel) and applications may override at any point before that.
     *
     * Unlike [map], targets are not validated eagerly: the standard IDE action IDs are platform-guaranteed, and this
     * may run before the [ActionManager] service would be warm — a missing target still surfaces at invocation time as
     * an `Unregistered` rejection.
     */
    public fun installStandardMappings() {
        standardDefaults[JewelActions.Copy.id] = IdeActions.ACTION_COPY
        standardDefaults[JewelActions.Cut.id] = IdeActions.ACTION_CUT
        standardDefaults[JewelActions.Paste.id] = IdeActions.ACTION_PASTE
        standardDefaults[JewelActions.SelectAll.id] = IdeActions.ACTION_SELECT_ALL
    }

    @TestOnly
    internal fun clearForTests() {
        mappings.clear()
        standardDefaults.clear()
    }
}

internal data class PendingBridgeTrigger(val targetActionId: String, val trigger: ActionTrigger)

internal val bridgeInvokerTrigger = ThreadLocal<PendingBridgeTrigger?>()

internal fun resolveContextComponent(hostComponent: Component): Component {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (hostComponent is JewelComposePanelWrapper) {
        val activePopup = hostComponent.topActivePopupWrapper()
        if (activePopup != null) {
            return focusOwner?.takeIf { SwingUtilities.isDescendingFrom(it, activePopup) } ?: activePopup.composePanel
        }
    }
    return focusOwner?.takeIf { SwingUtilities.isDescendingFrom(it, hostComponent) } ?: hostComponent
}

internal fun findJewelComposePanelWrapper(component: Component?): JewelComposePanelWrapper? =
    component as? JewelComposePanelWrapper
        ?: component?.let {
            SwingUtilities.getAncestorOfClass(JewelComposePanelWrapper::class.java, it) as? JewelComposePanelWrapper
        }

/**
 * Bridge-side [ActionInvoker]: routes a mapped ID to its platform action, otherwise to the bridge-owned
 * [JewelActionBridgeAction], always via `ActionManager.tryToExecute` with the focused descendant inside the Jewel host
 * (falling back to the host component) as the context component — listeners, transactions, and dumb-mode handling stay
 * on the platform path.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class JewelBridgeActionInvoker(private val hostComponent: Component, private val actionManager: ActionManager) :
    ActionInvoker {
    /** Creates an invoker targeting [hostComponent] backed by the global [ActionManager]. */
    public constructor(hostComponent: Component) : this(hostComponent, ActionManager.getInstance())

    override fun invoke(action: JewelAction, trigger: ActionTrigger): ActionDispatchResult {
        val contextComponent = resolveContextComponent(hostComponent)
        val targetId = JewelActionMappings.ideActionIdFor(action.id) ?: action.id.value
        val wrapper = findJewelComposePanelWrapper(contextComponent)
        val selectAllDelegate =
            if (action.id == JewelActions.SelectAll.id && targetId == IdeActions.ACTION_SELECT_ALL) {
                wrapper?.selectAllBridgeAction?.takeIf {
                    wrapper.shortcutHostState?.resolveFocusedHandlerForComponent(
                        actionId = JewelActions.SelectAll.id,
                        targetComponent = wrapper.composePanel,
                        allowCrossSurfaceFallback = !wrapper.isPopupWrapper,
                        excludedComponents = if (wrapper.isPopupWrapper) emptySet() else wrapper.activePopupPanels(),
                    ) != null
                }
            } else {
                null
            }
        val target =
            selectAllDelegate
                ?: actionManager.getAction(targetId)
                ?: return ActionDispatchResult.Rejected(ActionDispatchRejection.Unregistered)
        val previousTrigger = bridgeInvokerTrigger.get()
        val propagateTrigger = target is JewelActionBridgeAction
        if (propagateTrigger) {
            bridgeInvokerTrigger.set(PendingBridgeTrigger(targetId, trigger))
        }
        val app = ApplicationManager.getApplication()
        val writeAllowed = app?.isWriteAccessAllowed == true
        var ignoredDuringExecution = false
        val listenerDisposable =
            if (app != null && !app.isDisposed) {
                Disposer.newDisposable("JewelBridgeActionInvoker").also { disposable ->
                    app.messageBus
                        .connect(disposable)
                        .subscribe(
                            AnActionListener.TOPIC,
                            object : AnActionListener {
                                override fun afterActionPerformed(
                                    performedAction: AnAction,
                                    event: AnActionEvent,
                                    result: AnActionResult,
                                ) {
                                    if (performedAction === target && result.isIgnored) {
                                        ignoredDuringExecution = true
                                    }
                                }
                            },
                        )
                }
            } else {
                null
            }
        val callback =
            try {
                // Keep inputEvent null so IJPL never anchors popups at a fabricated (0, 0) coordinate;
                // now = true runs synchronously on EDT, so bridgeInvokerTrigger preserves trigger identity
                // and callback.isRejected reflects update()-time disablement or execution-time rejection immediately.
                actionManager.tryToExecute(target, null, contextComponent, JEWEL_ACTION_PLACE, true)
            } finally {
                listenerDisposable?.let(Disposer::dispose)
                if (propagateTrigger) {
                    if (previousTrigger == null) {
                        bridgeInvokerTrigger.remove()
                    } else {
                        bridgeInvokerTrigger.set(previousTrigger)
                    }
                }
            }
        callback.doWhenProcessed { findJewelShortcutHost(hostComponent)?.presentations?.invalidate() }
        if (callback.isRejected) {
            val reason =
                when {
                    ignoredDuringExecution || writeAllowed -> ActionDispatchRejection.RejectedByHost
                    callback.error == "action is disabled (early check)" -> ActionDispatchRejection.Disabled
                    else -> ActionDispatchRejection.RejectedByHost
                }
            return ActionDispatchResult.Rejected(reason)
        }
        return ActionDispatchResult.Dispatched
    }
}

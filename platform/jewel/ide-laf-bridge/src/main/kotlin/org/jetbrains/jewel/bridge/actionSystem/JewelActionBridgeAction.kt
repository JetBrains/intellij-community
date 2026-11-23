// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.util.ui.EDT
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.JewelSelectAllBridgeAction
import org.jetbrains.jewel.bridge.actionSystem.PresentationIconDescriptors.restoreIconDescriptorFrom
import org.jetbrains.jewel.bridge.actionSystem.PresentationIconDescriptors.setResolvedIconDescriptor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.shortcut.ActionPresentation
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionPropertyNames
import org.jetbrains.jewel.foundation.shortcut.ActionResolution
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.MenuDismissPolicy
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.toComposeKeyEvent

/**
 * The generic bridge action for Jewel commands, intended for normal plugin.xml `<action>` registration (its IJPL action
 * ID must equal the corresponding [JewelActionId] value) or runtime registration through `JewelBridgeActionRegistry`.
 *
 * It holds no Compose state: `update()` and `actionPerformed()` obtain the action's own registered ID from
 * [ActionManager], locate the focused Jewel host from the event's context component, and resolve the nearest focused
 * enabled `Modifier.shortcut` binding at that moment. Declarative and runtime actions are therefore identical from the
 * keymap's perspective, and a stale binding can never execute: perform always re-resolves.
 *
 * Runs on EDT ([ActionUpdateThread.EDT]): presentation resolution, icon conversion, and fallback host lookup execute on
 * the EDT.
 *
 * Enabled in modal contexts by design: Jewel content hosted inside modal dialogs participates in shortcut dispatch like
 * any other surface, and execution is already gated on a focused enabled binding, so the modal flag adds no
 * reachability an unfocused surface would not have.
 *
 * Implements [ActionPromoter] via [suppress] (deliberately not [promote]) so when multiple Jewel actions share an IDE
 * shortcut, strictly outer Jewel bindings are suppressed in favor of the innermost focused enabled binding without
 * jumping ahead of platform `ActionPromoter` extensions (such as `SpeedSearchActionPromoter`). Residual limitation: if
 * an enabled non-Jewel IDE action happens to sit between the outer and inner Jewel actions in keymap iteration order,
 * suppressing the outer Jewel action lets that non-Jewel action run before the inner Jewel action; likewise, if a
 * subclass overrides `update()` to disable itself despite an enabled Compose binding, it may still suppress an outer
 * Jewel binding.
 *
 * The class is deliberately not [DumbAware]: a Jewel binding's handler is arbitrary application code. Declare
 * [DumbAwareJewelActionBridgeAction] in plugin.xml for actions whose every focused handler is safe during indexing;
 * runtime registrations through `JewelBridgeActionRegistry` always use this class.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class JewelActionBridgeAction : AnAction(), ActionPromoter {
    init {
        isEnabledInModalContext = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        resetPresentationToTemplate(event)
        val id = ActionManager.getInstance().getId(this)
        val host = if (id != null) jewelHostStateFor(event) else null
        if (id == null || host == null) {
            event.presentation.isEnabled = false
            return
        }
        val actionId = JewelActionId(id)
        val presentation = host.presentationFor(actionId)
        if (presentation.resolution != ActionResolution.Resolved) {
            // Template visibility, text (including any place-specific <override-text>), description, icon, and client
            // properties are restored above: a declared action stays discoverable in menus while refusing execution
            // without a focused binding.
            event.presentation.isEnabled = false
            return
        }
        event.presentation.isEnabled = presentation.enabled
        val override = host.focusedPresentationOverride(actionId)
        applyPresentationFields(event, presentation, override)
        applyClientProperties(event, presentation)
    }

    private fun applyPresentationFields(
        event: AnActionEvent,
        presentation: ActionPresentation,
        override: ActionPresentationOverride?,
    ) {
        if (override != null) {
            val textOverride = override.text
            if (textOverride is PresentationValue.Set) {
                event.presentation.setText(textOverride.value, false)
            }
            val descriptionOverride = override.description
            if (descriptionOverride is PresentationValue.Set) {
                event.presentation.description = descriptionOverride.value
            }
            val visibleOverride = override.visible
            if (visibleOverride is PresentationValue.Set) {
                event.presentation.isVisible = visibleOverride.value
            }
            val iconOverride = override.icon
            if (iconOverride is PresentationValue.Set) {
                event.presentation.icon = swingIconFor(iconOverride.value)
                event.presentation.setResolvedIconDescriptor(iconOverride.value)
            }
            val dismissOverride = override.menuDismissPolicy
            if (dismissOverride is PresentationValue.Set) {
                event.presentation.keepPopupOnPerform =
                    dismissOverride.value?.toPlatform() ?: templatePresentation.keepPopupOnPerform
            }
            return
        }
        event.presentation.isVisible = presentation.visible
        if (presentation.text != templatePresentation.text) {
            event.presentation.setText(presentation.text, false)
        }
        presentation.description?.let { event.presentation.description = it }
        presentation.icon?.let { descriptor ->
            swingIconFor(descriptor)?.let { event.presentation.icon = it }
            event.presentation.setResolvedIconDescriptor(descriptor)
        }
        presentation.menuDismissPolicy?.let { event.presentation.keepPopupOnPerform = it.toPlatform() }
    }

    private fun applyClientProperties(event: AnActionEvent, presentation: ActionPresentation) {
        // Presentation properties are IJPL client properties by another name, and both sides key them by
        // name (Presentation stores its user map by Key.toString()), so they round-trip verbatim — including
        // toggle state, which is Toggleable.SELECTED_KEY on one side and ActionPropertyNames.Selected on the
        // other. Track keys written here so subsequent updates restore them to template values cleanly.
        if (presentation.properties.isEmpty()) return
        val written = HashSet<String>(presentation.properties.size)
        for ((name, value) in presentation.properties) {
            if (isReservedBridgeClientProperty(name)) continue
            val key = bridgeClientPropertyKey(name)
            @Suppress("UNCHECKED_CAST") event.presentation.putClientProperty(key as Key<Any>, value)
            written.add(name)
        }
        if (written.isNotEmpty()) {
            event.presentation.putClientProperty(WRITTEN_PROPERTIES_KEY, written)
        }
    }

    private fun resetPresentationToTemplate(event: AnActionEvent) {
        val presentation = event.presentation
        presentation.isVisible = templatePresentation.isVisible
        presentation.restoreTextWithMnemonic(templatePresentation)
        applyTextOverride(event)
        presentation.description = templatePresentation.description
        presentation.icon = templatePresentation.icon
        presentation.restoreIconDescriptorFrom(templatePresentation)
        presentation.keepPopupOnPerform = templatePresentation.keepPopupOnPerform
        val previouslyWritten = presentation.getClientProperty(WRITTEN_PROPERTIES_KEY)
        if (!previouslyWritten.isNullOrEmpty()) {
            for (name in previouslyWritten) {
                if (isReservedBridgeClientProperty(name)) continue
                val key = bridgeClientPropertyKey(name)

                @Suppress("UNCHECKED_CAST") val typedKey = key as Key<Any>
                presentation.putClientProperty(typedKey, templatePresentation.getClientProperty(typedKey))
            }
            presentation.putClientProperty(WRITTEN_PROPERTIES_KEY, null)
        }
    }

    override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? {
        if (actions.firstOrNull { it is JewelActionBridgeAction } !== this) return null
        return suppressCompetingJewelActions(actions, context)
    }

    /**
     * Converting an icon descriptor to a Swing icon loads image resources, and `update()` runs on the platform's
     * cadence (up to twice a second per place), so the conversion is weakly memoised on the descriptor's value
     * identity. Weak references ensure a long-lived platform action never retains plugin-owned icons or classloaders
     * after the contributing binding blurs or unloads.
     */
    @Volatile private var cachedIconSourceRef: WeakReference<IconDescriptor>? = null

    @Volatile private var cachedSwingIconRef: WeakReference<javax.swing.Icon>? = null

    private fun swingIconFor(icon: IconDescriptor?): javax.swing.Icon? {
        if (icon == null) {
            cachedIconSourceRef = null
            cachedSwingIconRef = null
            return null
        }
        val cachedSource = cachedIconSourceRef?.get()
        val cachedSwing = cachedSwingIconRef?.get()
        if (cachedSource == icon && cachedSwing != null) return cachedSwing
        val converted = icon.createSwingIcon()
        cachedIconSourceRef = WeakReference(icon)
        cachedSwingIconRef = WeakReference(converted)
        return converted
    }

    override fun actionPerformed(event: AnActionEvent) {
        val id = ActionManager.getInstance().getId(this)
        val trigger = consumePendingTriggerOrFromEvent(id, event)
        if (id == null) return
        val host = jewelHostStateFor(event) ?: return
        val actionId = JewelActionId(id)
        val handler = host.resolveFocusedHandler(actionId) ?: return
        val keyEvent = event.inputEvent as? KeyEvent
        if (
            keyEvent?.id == KeyEvent.KEY_PRESSED &&
                host.consumeIfRepeatSuppressed(actionId, keyEvent.toComposeKeyEvent())
        ) {
            return
        }
        // Exactly one Jewel event per completed bridge-owned invocation, whatever invoked the platform
        // action (IDE keymap, menus, tryToExecute). Mapped platform actions never come through here and
        // deliberately never emit.
        host.runResolvedInvocation(actionId, trigger, handler)
    }

    private fun consumePendingTriggerOrFromEvent(actionId: String?, event: AnActionEvent): ActionTrigger {
        val pending = bridgeInvokerTrigger.get()
        if (pending != null && pending.targetActionId == actionId) {
            bridgeInvokerTrigger.remove()
            return pending.trigger
        }
        return when (event.inputEvent) {
            // The invoking sequence belongs to the IDE keymap, not a Jewel keymap: report a keyboard
            // trigger without claiming a Jewel sequence.
            is KeyEvent -> ActionTrigger.Keyboard(null)
            is MouseEvent -> ActionTrigger.Pointer
            else -> ActionTrigger.Programmatic
        }
    }

    private fun MenuDismissPolicy.toPlatform(): KeepPopupOnPerform =
        when (this) {
            MenuDismissPolicy.Dismiss -> KeepPopupOnPerform.Never
            MenuDismissPolicy.KeepIfRequested -> KeepPopupOnPerform.IfRequested
            MenuDismissPolicy.KeepIfPreferred -> KeepPopupOnPerform.IfPreferred
            MenuDismissPolicy.KeepAlways -> KeepPopupOnPerform.Always
        }

    private fun jewelHostStateFor(event: AnActionEvent): JewelShortcutHostState? =
        jewelHostStateFromContext(event.dataContext)
}

private val WRITTEN_PROPERTIES_KEY: Key<Set<String>> =
    Key.create("org.jetbrains.jewel.bridge.actionSystem.writtenProperties")
private val customBridgePropertyKeys = ConcurrentHashMap<String, Key<Any>>()

// Presentation stores client properties by key.toString(), whereas Key.equals is reference identity; check
// reserved keys by name before creating or looking up a Key instance.
internal fun isReservedBridgeClientProperty(name: String): Boolean =
    name == CustomComponentAction.COMPONENT_KEY.toString() || name == WRITTEN_PROPERTIES_KEY.toString()

internal fun bridgeClientPropertyKey(name: String): Key<*> =
    when (name) {
        ActionPropertyNames.Selected -> Toggleable.SELECTED_KEY
        ActionPropertyNames.ShowTextInToolbar -> ActionUtil.SHOW_TEXT_IN_TOOLBAR
        ActionPropertyNames.HideDropdownIcon -> ActionUtil.HIDE_DROPDOWN_ICON
        else -> customBridgePropertyKeys.computeIfAbsent(name) { Key.create<Any>(it) }
    }

internal fun jewelHostStateFromContext(context: DataContext): JewelShortcutHostState? {
    // The wrapper sinks its host into the data context (JewelComposePanelWrapper.uiDataSnapshot),
    // so the normal path reads the snapshotted value without traversing the Swing hierarchy.
    context.getData(JEWEL_SHORTCUT_HOST_STATE)?.let {
        return it
    }
    // Never walk the Swing component hierarchy off-EDT (e.g., BGT ActionPromoter / update calls).
    if (!EDT.isCurrentThreadEdt()) return null
    // Fallback for contexts built without the wrapper's snapshot (e.g. a bare component context on EDT).
    val component = context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
    return findJewelShortcutHost(component)
}

internal fun suppressCompetingJewelActions(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    if (actions.size < 2) return null
    // Read only the snapshotted host from DataContext so BGT ActionPromoter calls never touch Swing hierarchy.
    val host = context.getData(JEWEL_SHORTCUT_HOST_STATE) ?: return null
    val actionManager = ActionManager.getInstance()
    val isDumb = context.getData(CommonDataKeys.PROJECT)?.let { DumbService.isDumb(it) } == true

    var maxDepth = -1
    val candidates = ArrayList<Pair<AnAction, Int>>()
    for (candidate in actions) {
        val actionId = resolveJewelActionId(candidate, actionManager) ?: continue
        if (isDumb && !candidate.isDumbAware) continue
        val depth = host.focusedBindingDepth(actionId)
        if (depth < 0) continue
        candidates.add(candidate to depth)
        if (depth > maxDepth) {
            maxDepth = depth
        }
    }
    if (candidates.size < 2) return null
    val losers = candidates.filter { it.second < maxDepth }.map { it.first }
    return losers.ifEmpty { null }
}

private fun resolveJewelActionId(candidate: AnAction, actionManager: ActionManager): JewelActionId? {
    val root = ActionUtil.getDelegateChainRootAction(candidate)
    return when (root) {
        is JewelActionBridgeAction -> actionManager.getId(root)?.let(::JewelActionId)
        is JewelSelectAllBridgeAction -> JewelActions.SelectAll.id
        else -> null
    }
}

/**
 * Platform `actionPromoter` extension that extends innermost-wins suppression to wrapped Jewel actions (such as
 * component-scoped `ActionUtil.wrap(id)` entries) and the panel-scoped `SelectAll` bridge action, which do not
 * implement [ActionPromoter] directly. Uses [suppress] only (never [promote]) so Jewel actions never cut ahead of
 * platform promoters.
 */
@ApiStatus.Internal
@InternalJewelApi
public class JewelActionPromoter : ActionPromoter {
    override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? =
        suppressCompetingJewelActions(actions, context)
}

/**
 * The focused surface's shortcut host, snapshotted into the data context by
 * [org.jetbrains.jewel.bridge.JewelComposePanelWrapper] whenever it is in the context component chain.
 */
@get:ApiStatus.Internal
@InternalJewelApi
// The annotation targets the getter so the generated file-facade method is not exposed in the stable API dump.
public val JEWEL_SHORTCUT_HOST_STATE: DataKey<JewelShortcutHostState> = DataKey.create("JewelShortcutHostState")

/**
 * The [DumbAware] declaration variant of [JewelActionBridgeAction], for plugin.xml `<action>` entries whose focused
 * handlers are all safe to run during indexing. Runtime registrations never use this class: the registry cannot know
 * what a future focused handler will do, so it always registers the non-dumb-aware base class.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class DumbAwareJewelActionBridgeAction : JewelActionBridgeAction(), DumbAware

/** Focused-host lookup shared with the invoker; exposed for tests. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun findJewelShortcutHost(component: Component?): JewelShortcutHostState? {
    var current: Component? = component
    while (current != null) {
        if (current is JewelComposePanelWrapper) return current.shortcutHostState
        current = current.parent
    }
    return null
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import java.awt.Component
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * One shortcut-dispatch host per Compose surface (a standalone window, or a bridge panel).
 *
 * Install [resolverRootModifier] at the surface's content root, then:
 * - standalone: pass [onPreviewKeyEvent] to the `Window(onPreviewKeyEvent = …)` parameter. That hook runs before scene
 *   dispatch and sees KEY_TYPED events, which is required for typed suppression — a root `Modifier.onPreviewKeyEvent`
 *   sees no typed events and cannot prevent a claimed printable key from inserting its character.
 * - IJPL bridge: the panel wrapper consults [claimsKeyDown] from `KeyboardAwareContainer` to skip
 *   `IdeKeyEventDispatcher` for claimed strokes and open menu scopes; commands stay with the IJPL keymap. **Focusable**
 *   Jewel `Popup` components attach [resolverRootModifier] and propagate [onPreviewKeyEvent] automatically when
 *   [LocalJewelShortcutHost] is in scope (non-focusable popups such as tooltips leave the root stack untouched);
 *   standalone dialogs must install [resolverRootModifier] and pass [onPreviewKeyEvent].
 *
 * **Threading:** dispatch is UI-thread-synchronous — [onPreviewKeyEvent], [claimsKeyDown], and [runResolvedInvocation]
 * must run on the surface's UI thread (the AWT event dispatch thread in production), and the bound handlers they invoke
 * run there too. Violations are logged as errors, or thrown when [org.jetbrains.jewel.foundation.JewelFlags.strictMode]
 * is enabled. Presentation sampling is different: [presentationFor] and [presentations] read an atomically published
 * snapshot of the focused bindings that may be up to one UI frame stale. That snapshot read is thread-safe, but whether
 * sampling as a whole is depends on the backing: the IJPL bridge schedules platform action updates asynchronously via
 * the platform updater and returns cached samples off-EDT; with the standalone default backing, resolving a binding
 * that computes its presentation from the focused Compose tree must run on the surface's UI thread.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class JewelShortcutHostState(
    private val registry: JewelActionRegistry? = null,
    private val chordTimeoutMillis: Long,
    private val keymapProvider: () -> JewelKeymap,
) {
    /** Creates a shortcut host with the default [ShortcutDispatchEngine.DEFAULT_CHORD_TIMEOUT_MILLIS] chord timeout. */
    public constructor(
        registry: JewelActionRegistry? = null,
        keymapProvider: () -> JewelKeymap,
    ) : this(registry, ShortcutDispatchEngine.DEFAULT_CHORD_TIMEOUT_MILLIS, keymapProvider)

    /** Attached resolver roots ordered outermost-first; popup/dialog overlays sit at the tail. */
    @Volatile private var attachedRoots: List<ShortcutResolverRootNode> = emptyList()

    private val logger = myLogger()

    /** Action IDs already reported as unregistered; presentation polls must not spam the log. */
    private val reportedUnregistered = ConcurrentHashMap.newKeySet<JewelActionId>()

    private var awaitingSecondStrokeState by mutableStateOf(false)
    private var chordTimeoutJob: Job? = null

    private fun isActionRegistered(actionId: JewelActionId): Boolean =
        registry == null || registry.definition(actionId) != null

    private fun activeCommandRoots(): List<ShortcutResolverRootNode> {
        val roots = attachedRoots
        val lastFocused = roots.indexOfLast { it.isWindowFocusedState }
        if (lastFocused < 0) return roots
        // When a newer window (such as a standalone modal dialog) has focus, keep only focused-window roots and any
        // overlay roots stacked above them so deactivated owner bindings cannot fire behind the modal window.
        return roots.filterIndexed { index, root -> root.isWindowFocusedState || index > lastFocused }
    }

    private val engine: ShortcutDispatchEngine =
        ShortcutDispatchEngine(
            keymap = keymapProvider,
            focusedBindings = { activeCommandRoots().flatMap(ShortcutResolverRootNode::engineBindings) },
            // Claims resolve only against the topmost attached root so outer surface claims cannot swallow keys
            // while a focusable popup is active (including bridge JBPopups whose AWT focus remains on the owner).
            focusedClaims = { attachedRoots.lastOrNull()?.engineClaims().orEmpty() },
            chordTimeoutMillis = chordTimeoutMillis,
            clockMillis = System::currentTimeMillis,
            onPendingChordChanged = ::handlePendingChordChanged,
            isActionRegistered = ::isActionRegistered,
        )

    private fun handlePendingChordChanged(pending: JewelKeyStroke?) {
        awaitingSecondStrokeState = pending != null
        chordTimeoutJob?.cancel()
        chordTimeoutJob = null
        if (pending != null && chordTimeoutMillis > 0L) {
            // Anchor on a focused root (or the primary root) so detaching an unfocused secondary root cannot kill the
            // timer.
            val scope =
                (attachedRoots.firstOrNull { it.subtreeHadFocusState } ?: attachedRoots.firstOrNull())
                    ?.activeCoroutineScope() ?: return
            chordTimeoutJob =
                scope.launch {
                    delay(chordTimeoutMillis)
                    engine.cancelPendingChord()
                }
        }
    }

    /** Fired after every consumed dispatch; drives Presentation Assistant-style overlays and tests. */
    public var onDispatch: ((DispatchDecision.Consumed) -> Unit)? = null

    private val eventSource = MutableActionEventSource()

    /** One event per completed Jewel-owned invocation (keyboard and [invoker]). */
    public val events: ActionEventSource
        get() = eventSource

    /**
     * Demand-driven presentation sampling for this host. Poll on your own signals via
     * [ActionPresentationScheduler.invalidate]; keyboard dispatches invalidate automatically.
     */
    public val presentations: ActionPresentationScheduler = ActionPresentationScheduler(::samplePresentation)

    // Keep the registration listener strongly referenced only for this host's lifetime so DefaultJewelActionRegistry
    // holds only a WeakReference and cannot retain disposed hosts.
    @Suppress("UnusedPrivateProperty")
    private val registryChangeListener: ((JewelActionId) -> Unit)? =
        (registry as? DefaultJewelActionRegistry)?.let { defaultRegistry ->
            val hostRef = WeakReference(this)
            val listener: (JewelActionId) -> Unit = { id -> hostRef.get()?.presentations?.invalidate(id) }
            defaultRegistry.addRegistrationListener(listener)
            listener
        }

    /**
     * Normal host action execution. The standalone default resolves the nearest focused enabled Jewel binding and emits
     * through [events]; the IJPL bridge substitutes a platform-routing invoker so `ActionManager` update, enablement,
     * and listeners stay authoritative there.
     */
    public var invoker: ActionInvoker =
        object : ActionInvoker {
            override fun invoke(action: JewelAction, trigger: ActionTrigger): ActionDispatchResult {
                if (!isActionRegistered(action.id)) {
                    return ActionDispatchResult.Rejected(ActionDispatchRejection.Unregistered)
                }
                val binding =
                    engine.resolveFocusedBinding(action.id)
                        ?: return ActionDispatchResult.Rejected(engine.resolveFocusedBindingRejection(action.id))
                runResolvedInvocation(action.id, trigger, binding.onInvoke)
                return ActionDispatchResult.Dispatched
            }
        }

    /**
     * Runs [handler] as one completed Jewel-owned invocation of [actionId]: exactly one [events] emission, then a
     * presentation re-sample. This is the emission point for host integrations that resolve focused handlers themselves
     * (the IJPL bridge action); keyboard dispatch through [onPreviewKeyEvent] emits on its own and must not be routed
     * through here too.
     */
    public fun runResolvedInvocation(actionId: JewelActionId, trigger: ActionTrigger, handler: () -> Unit) {
        checkUiThread("runResolvedInvocation")
        handler()
        eventSource.emit(ActionInvocation(actionId, (trigger as? ActionTrigger.Keyboard)?.sequence, trigger))
        presentations.invalidate()
    }

    /** Whether the dispatch engine is currently waiting for the second stroke of a chord (observable in Compose). */
    public val isAwaitingSecondStroke: Boolean
        get() = awaitingSecondStrokeState

    /** Cancels an in-flight two-stroke chord without consuming input. Returns `true` if a chord was pending. */
    public fun cancelPendingChord(): Boolean = engine.cancelPendingChord()

    /** Innermost focused enabled handler for [actionId]; null when none (action disabled here). */
    public fun resolveFocusedHandler(actionId: JewelActionId): (() -> Unit)? =
        engine.resolveFocusedBinding(actionId)?.onInvoke

    /**
     * Innermost focused enabled handler for [actionId] scoped to resolver roots attached to [targetComponent]. When no
     * root matches [targetComponent], falls back to [resolveFocusedHandler] only if [allowCrossSurfaceFallback] is
     * `true`.
     */
    @ApiStatus.Internal
    @InternalJewelApi
    public fun resolveFocusedHandlerForComponent(
        actionId: JewelActionId,
        targetComponent: Component?,
        allowCrossSurfaceFallback: Boolean,
        excludedComponents: Set<Component> = emptySet(),
    ): (() -> Unit)? {
        if (targetComponent != null) {
            val matchingRoots =
                if (allowCrossSurfaceFallback) {
                    attachedRoots.filter { it.ownerComponent !in excludedComponents }
                } else {
                    attachedRoots.filter { it.ownerComponent === targetComponent }
                }
            if (matchingRoots.isNotEmpty()) {
                return resolveBindingIn(matchingRoots.flatMap(ShortcutResolverRootNode::engineBindings), actionId)
                    ?.onInvoke
            }
        }
        return if (allowCrossSurfaceFallback) resolveFocusedHandler(actionId) else null
    }

    /**
     * The active keymap's shortcuts for [actionId], delegating to [registry] when the host manages bindings externally.
     */
    public fun shortcutsFor(actionId: JewelActionId): List<JewelKeySequence> =
        registry?.shortcutsFor(actionId) ?: keymapProvider().shortcutsFor(actionId)

    /**
     * The action's current presentation for this host: the failure states (Unregistered when a [registry] is installed
     * and does not know the ID; NoFocusedBinding otherwise) or the nearest focused enabled binding's
     * [ActionPresentationOverride] merged over the action's template.
     *
     * **Threading:** reads an atomically published snapshot of the focused bindings; the result may be up to one UI
     * frame stale, which the next demand-driven sample corrects. The IJPL bridge schedules platform action updates
     * asynchronously via the platform updater and returns cached samples off-EDT; with the standalone default backing,
     * resolving a binding that computes its presentation from the focused Compose tree must run on the surface's UI
     * thread. Presentation is advisory: dispatch itself never consults it.
     */
    public fun presentationFor(actionId: JewelActionId): ActionPresentation = samplePresentation(actionId)

    private fun samplePresentation(actionId: JewelActionId): ActionPresentation {
        // Every branch below starts from the action's template presentation, so the icon, description and
        // authored flags survive regardless of how the action resolved — the template is the shared base, and
        // resolution only decides enablement and which overrides apply on top.
        val definition = registry?.definition(actionId)
        if (registry != null && definition == null) {
            if (reportedUnregistered.add(actionId)) {
                logger.warn(
                    "Action '${actionId.value}' is not registered with this host's action registry; " +
                        "controls bound to it render disabled. Further reports for it are suppressed."
                )
            }
            return ActionPresentation(
                text = actionId.value,
                enabled = false,
                resolution = ActionResolution.Unregistered,
            )
        }
        val template = definition?.action?.template

        // A host-computed sample (the platform's update() in the bridge) replaces the template as the base:
        // it is the same layer, only recomputed, so a binding override still applies on top of it.
        val live =
            registry?.sampledPresentation(actionId)?.takeIf {
                it.resolution != ActionResolution.Unregistered && it.resolution != ActionResolution.HostUnavailable
            }

        val binding = engine.resolveFocusedBinding(actionId)
        if (binding != null) {
            // Preserve a host-computed sample's own enablement (for mapped platform actions, the platform's
            // update() stays authoritative); template-derived samples are enabled because a focused binding resolved.
            val base =
                live?.copy(resolution = ActionResolution.Resolved)
                    ?: template?.sampled(enabled = true, resolution = ActionResolution.Resolved)
                    // No registry: the binding itself is the only source of authored text.
                    ?: ActionPresentation(text = binding.origin, enabled = true, resolution = ActionResolution.Resolved)
            return binding.presentationOverride.mergeOver(base)
        }

        // Native host-sampled actions (e.g. IDE actions with no Compose Modifier.shortcut binding) keep their own
        // resolution so controls bound to them see ActionResolution.Resolved rather than NoFocusedBinding.
        return live
            ?: template?.sampled(enabled = false, resolution = ActionResolution.NoFocusedBinding)
            ?: ActionPresentation(
                text = actionId.value,
                enabled = false,
                resolution = ActionResolution.NoFocusedBinding,
            )
    }

    public val resolverRootModifier: Modifier
        get() = Modifier then ShortcutResolverRootElement(this)

    private val menuScopes = ArrayDeque<MenuShortcutScope>()

    /**
     * Opens a menu-local shortcut scope for a menu that just became visible. While at least one scope is open, the
     * innermost (most recently opened) scope's strokes resolve *before* ordinary dispatch, and matched strokes are
     * consumed with typed suppression — the single dispatcher for open menus, absorbing what menu-local key handling
     * used to do so the two can never race. Close the scope when the menu closes; scopes must be closed in reverse
     * opening order (innermost first).
     */
    public fun openMenuShortcutScope(): MenuShortcutScope {
        val scope = MenuShortcutScope(this)
        menuScopes.addLast(scope)
        return scope
    }

    internal fun closeMenuScope(scope: MenuShortcutScope) {
        menuScopes.remove(scope)
    }

    private fun resolveMenuScopeAction(stroke: JewelKeyStroke?): (() -> Unit)? {
        if (stroke == null) return null
        return menuScopes.lastOrNull()?.actionFor(stroke)
    }

    /** Window-level AWT pre-scene handler; consumes menu, claim, and mapped strokes plus suppressed typed events. */
    public fun onPreviewKeyEvent(event: KeyEvent): Boolean = onPreviewKeyEvent(event, hostOs)

    internal fun onPreviewKeyEvent(event: KeyEvent, os: OS): Boolean {
        checkUiThread("onPreviewKeyEvent")
        when (event.type) {
            KeyEventType.KeyDown -> {
                val isAltGr = JewelKeyStroke.isAltGrKeyDown(event, os)
                val stroke = if (isAltGr) null else JewelKeyStroke.fromKeyDownOrNull(event, os)
                // An AltGr typing press yields no stroke, so `onClaimKeyDown(null)` will skip its normal
                // `suppressNextTyped = false` reset. Clear any stale latch here without disturbing a live chord
                // or repeat latch; a matching raw claim at step 3 still re-arms suppression for this press.
                if (isAltGr) {
                    engine.clearTypedSuppression()
                }
                // 1) Open Jewel menu scope.
                resolveMenuScopeAction(stroke)?.let { action ->
                    engine.cancelPendingChord()
                    engine.armTypedSuppression()
                    action()
                    onDispatch?.invoke(
                        DispatchDecision.Consumed(
                            invokedActionId = null,
                            invokedSequence = stroke?.let(::JewelKeySequence),
                            route = DispatchDecision.Consumed.Route.Claim,
                        )
                    )
                    presentations.invalidate()
                    return true
                }
                // 2) Focused sequence claims.
                val claimDecision = engine.onClaimKeyDown(stroke)
                if (claimDecision is DispatchDecision.Consumed) {
                    notifyConsumedDispatch(claimDecision)
                    return true
                }
                // 3) Focused raw key claims (deliberately local, innermost enabled wins, ahead of host keymap).
                resolveRawClaim(event)?.let { claim ->
                    engine.onRawClaimConsumed()
                    claim.onKeyEvent(event)
                    notifyConsumedDispatch(
                        DispatchDecision.Consumed(
                            invokedActionId = null,
                            invokedSequence = null,
                            route = DispatchDecision.Consumed.Route.Claim,
                        )
                    )
                    return true
                }
                // 4) Pending two-stroke chord / exact keymap command / chord prefix.
                val keymapDecision = engine.onKeymapKeyDown(stroke)
                if (keymapDecision is DispatchDecision.Consumed) {
                    notifyConsumedDispatch(keymapDecision)
                    return true
                }
                return false
            }
            KeyEventType.KeyUp -> {
                engine.onKeyUp(event.key)
                return false
            }
            // KEY_TYPED and other unknown AWT events surface as Unknown at this hook.
            else -> {
                val suppressed = engine.onTypedEvent()
                if (suppressed) {
                    onDispatch?.invoke(
                        DispatchDecision.Consumed(
                            invokedActionId = null,
                            invokedSequence = null,
                            route = DispatchDecision.Consumed.Route.TypedSuppressed,
                        )
                    )
                }
                return suppressed
            }
        }
    }

    private fun notifyConsumedDispatch(decision: DispatchDecision.Consumed) {
        onDispatch?.invoke(decision)
        if (
            decision.route == DispatchDecision.Consumed.Route.Claim ||
                decision.route == DispatchDecision.Consumed.Route.Keymap
        ) {
            eventSource.emit(
                ActionInvocation(
                    decision.invokedActionId,
                    decision.invokedSequence,
                    ActionTrigger.Keyboard(decision.invokedSequence),
                )
            )
            presentations.invalidate()
        }
    }

    /**
     * The IJPL bridge veto: true when an open menu scope or focused claim owns this key-down. Only claims and open menu
     * scopes veto the IDE keymap; commands remain IJPL actions resolved through the platform keymap.
     */
    public fun claimsKeyDown(event: KeyEvent): Boolean = claimsKeyDown(event, hostOs)

    internal fun claimsKeyDown(event: KeyEvent, os: OS): Boolean {
        checkUiThread("claimsKeyDown")
        val stroke = JewelKeyStroke.fromKeyDownOrNull(event, os)
        // Delegates to the engine's claim resolution so veto and delivery can never disagree: a disabled inner
        // claim with blocksOuterClaims suppresses both, and only one-stroke claims veto.
        return (stroke != null && (resolveMenuScopeAction(stroke) != null || engine.claimsStroke(stroke))) ||
            resolveRawClaim(event) != null
    }

    /**
     * Retires any stale `KEY_TYPED` suppression latch when the IJPL bridge observes an unclaimed non-modifier key-down
     * ahead of IDE keymap dispatch (for example, a Windows AltGr press held by `IdeKeyEventDispatcher` whose
     * `KEY_PRESSED` never reaches the bridge's delivery dispatcher before the resulting `KEY_TYPED`).
     */
    @ApiStatus.Internal
    @InternalJewelApi
    public fun retireStaleTypedSuppressionForUnclaimedKeyDown(event: KeyEvent) {
        checkUiThread("retireStaleTypedSuppressionForUnclaimedKeyDown")
        if (!JewelKeyStroke.isModifierOrUnmappable(event)) {
            engine.clearTypedSuppression()
        }
    }

    /**
     * Applies `OnceUntilRelease` repeat suppression for a host-dispatched command `KEY_PRESSED` event (used by the IJPL
     * bridge). Returns `true` when the press is an auto-repeat of an already-latched stroke and must be skipped.
     */
    @ApiStatus.Internal
    @InternalJewelApi
    public fun consumeIfRepeatSuppressed(actionId: JewelActionId, event: KeyEvent): Boolean {
        checkUiThread("consumeIfRepeatSuppressed")
        return engine.consumeIfRepeatSuppressed(actionId, JewelKeyStroke.fromKeyDownOrNull(event))
    }

    /**
     * Nesting-depth index of the winning focused enabled binding for [actionId] (higher is deeper/innermost), or `-1`
     * when none resolves. Used by the IJPL bridge to resolve shortcut collisions across competing Jewel actions.
     */
    @ApiStatus.Internal
    @InternalJewelApi
    public fun focusedBindingDepth(actionId: JewelActionId): Int = engine.resolveFocusedBindingIndex(actionId)

    /**
     * Per-place presentation override of the winning focused enabled binding for [actionId], or `null` when no focused
     * enabled binding resolves. Used by the IJPL bridge to distinguish explicit [PresentationValue.Set] overrides from
     * inherited template values.
     */
    @ApiStatus.Internal
    @InternalJewelApi
    public fun focusedPresentationOverride(actionId: JewelActionId): ActionPresentationOverride? =
        engine.resolveFocusedBinding(actionId)?.presentationOverride

    /** Clears chord/typed state; call on focus loss or host disposal. */
    public fun reset() {
        engine.reset()
    }

    internal fun attachRoot(node: ShortcutResolverRootNode) {
        if (attachedRoots.any { it === node }) return
        // Stable sort by ancestor-root depth keeps nested resolver roots outermost-first even when Compose runs
        // child onAttach() before parent onAttach() during initial composition, while preserving attach order for
        // separate-window overlays (which all have depth 0).
        attachedRoots = (attachedRoots + node).sortedBy(ShortcutResolverRootNode::rootNestingDepth)
    }

    internal fun detachRoot(node: ShortcutResolverRootNode) {
        val updated = attachedRoots.filter { it !== node }
        if (updated.size != attachedRoots.size) {
            attachedRoots = updated
            if (updated.isEmpty() || node.subtreeHadFocusState) {
                reset()
            }
            presentations.invalidate()
        }
    }

    private fun resolveRawClaim(event: KeyEvent): RawKeyClaimNode? = attachedRoots.lastOrNull()?.rawClaimFor(event)

    /**
     * Dispatch entry points and the handlers they invoke are bound to the surface's UI thread — the thread the resolver
     * root attached on (the AWT EDT in production, the test thread under a Compose test rule). Violations log an error,
     * or throw when [JewelFlags.strictMode] is on. No root attached means no surface, so no contract to enforce (plain
     * unit tests drive the engine directly).
     */
    private fun checkUiThread(entryPoint: String) {
        val expected = attachedRoots.firstOrNull { it.uiThread != null }?.uiThread ?: return
        val actual = Thread.currentThread()
        if (actual !== expected) {
            val message =
                "$entryPoint must run on the surface's UI thread ('${expected.name}') but was called on " +
                    "'${actual.name}'. Shortcut dispatch is UI-thread-synchronous; only presentation sampling " +
                    "(presentationFor) is safe from other threads."
            if (JewelFlags.strictMode) error(message) else logger.error(message)
        }
    }
}

/**
 * Menu-local shortcuts for one open menu, resolved by the owning [JewelShortcutHostState] ahead of ordinary dispatch
 * while this is the innermost open scope. Registrations are replaced wholesale on menu content changes ([replaceAll]);
 * [close] must be called when the menu closes.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class MenuShortcutScope internal constructor(private val host: JewelShortcutHostState) : AutoCloseable {
    private val entries = LinkedHashMap<JewelKeyStroke, () -> Unit>()

    /** Registers [onInvoke] for [stroke] inside this menu scope. */
    public fun register(stroke: JewelKeyStroke, onInvoke: () -> Unit) {
        entries[stroke] = onInvoke
    }

    /** Replaces all stroke registrations in this menu scope with [newEntries]. */
    public fun replaceAll(newEntries: Map<JewelKeyStroke, () -> Unit>) {
        entries.clear()
        entries.putAll(newEntries)
    }

    internal fun actionFor(stroke: JewelKeyStroke): (() -> Unit)? = entries[stroke]

    override fun close() {
        entries.clear()
        host.closeMenuScope(this)
    }
}

/** Remembers a [JewelShortcutHostState] for [registry] and [keymap]; updates cleanly when either parameter changes. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun rememberJewelShortcutHostState(
    registry: JewelActionRegistry? = null,
    keymap: () -> JewelKeymap,
): JewelShortcutHostState {
    val currentKeymap by rememberUpdatedState(keymap)
    return remember(registry) { JewelShortcutHostState(registry) { currentKeymap() } }
}

@InternalJewelApi
@ApiStatus.Internal
public class ShortcutResolverRootNode(public var state: JewelShortcutHostState) :
    Modifier.Node(),
    TraversableNode,
    KeyInputModifierNode,
    FocusEventModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {
    override val traverseKey: TraverseKey = TraverseKey

    private var subtreeHadFocus = false

    @Volatile private var windowWasFocused = false

    internal val subtreeHadFocusState: Boolean
        get() = subtreeHadFocus

    internal val isWindowFocusedState: Boolean
        get() = windowWasFocused

    /**
     * Focus-driven registrations replace per-keystroke subtree traversal: participant nodes register on focus-gain and
     * deregister on focus-loss/detach (see [ShortcutRegistrarNode]). The registry lives on this node — not on the host
     * state — because [ShortcutResolverRootElement.update] can swap the state under a stable root without any focus
     * transitions firing; node ownership keeps registrations valid across such swaps. Mutations happen on the UI
     * thread; each mutation publishes an immutable, outermost-first-sorted snapshot so presentation sampling can read
     * from any thread (at most one UI frame stale).
     */
    private val focusedBindingNodes = mutableListOf<ShortcutBindingNode>()
    private val focusedClaimNodes = mutableListOf<ShortcutClaimNode>()
    private val focusedRawClaimNodes = mutableListOf<RawKeyClaimNode>()

    @Volatile private var bindingSnapshot: List<ShortcutBindingNode> = emptyList()

    @Volatile private var claimSnapshot: List<ShortcutClaimNode> = emptyList()

    @Volatile private var rawClaimSnapshot: List<RawKeyClaimNode> = emptyList()

    /** The surface's UI thread, recorded at attach; dispatch entry points are bound to it. */
    @Volatile
    internal var uiThread: Thread? = null
        private set

    /** The Swing/Compose host component providing [LocalComponent] for this root, or `null` in headless tests. */
    @Volatile
    internal var ownerComponent: Component? = null
        private set

    internal fun activeCoroutineScope(): CoroutineScope? = if (isAttached) coroutineScope else null

    internal fun rootNestingDepth(): Int {
        if (!isAttached) return 0
        var depth = 0
        traverseAncestors(TraverseKey) {
            depth++
            true
        }
        return depth
    }

    internal fun register(node: ShortcutRegistrarNode) {
        registerNode(node)
        // The focused binding set just changed, so every sampled presentation is stale: a control bound to an
        // action that just became (un)available must re-render. Keyboard dispatch invalidates on its own, and the
        // IJPL bridge additionally rides the platform's action-update cadence, but a focus change alone has no
        // other signal — without this, standalone action components render whatever was true at first sample.
        state.presentations.invalidate()
    }

    private fun registerNode(node: ShortcutRegistrarNode) {
        when (node) {
            is ShortcutBindingNode -> {
                focusedBindingNodes.add(node)
                bindingSnapshot = sortedSnapshot(focusedBindingNodes)
            }
            is ShortcutClaimNode -> {
                focusedClaimNodes.add(node)
                claimSnapshot = sortedSnapshot(focusedClaimNodes)
            }
            is RawKeyClaimNode -> {
                focusedRawClaimNodes.add(node)
                rawClaimSnapshot = sortedSnapshot(focusedRawClaimNodes)
            }
        }
    }

    internal fun deregister(node: ShortcutRegistrarNode) {
        deregisterNode(node)
        state.presentations.invalidate()
    }

    private fun deregisterNode(node: ShortcutRegistrarNode) {
        // Re-sort on removal exactly as [registerNode] does: the backing lists are only ever appended to, so they
        // stay in registration (insertion) order, which is NOT depth order. A plain copy here would republish the
        // snapshot in insertion order and make the engine treat an outer node as innermost until the next
        // registration re-sorted it — a silent, intermittent wrong-target regression.
        when (node) {
            is ShortcutBindingNode -> {
                focusedBindingNodes.remove(node)
                bindingSnapshot = sortedSnapshot(focusedBindingNodes)
            }
            is ShortcutClaimNode -> {
                focusedClaimNodes.remove(node)
                claimSnapshot = sortedSnapshot(focusedClaimNodes)
            }
            is RawKeyClaimNode -> {
                focusedRawClaimNodes.remove(node)
                rawClaimSnapshot = sortedSnapshot(focusedRawClaimNodes)
            }
        }
    }

    /**
     * Outermost-first by nesting depth (the engine treats the LAST entries as innermost), matching the pre-order
     * contract of the dispatch tests. Depth is recomputed on every registration change, so tree edits that fire focus
     * or attach events can never leave a stale order behind; the sort is stable, so registration order breaks ties.
     */
    private fun <T : ShortcutRegistrarNode> sortedSnapshot(nodes: List<T>): List<T> =
        nodes.sortedBy(ShortcutRegistrarNode::nestingDepth)

    internal fun engineBindings(): List<EngineBinding> =
        bindingSnapshot.map { node ->
            EngineBinding(
                actionId = node.action.id,
                enabled = node.enabled,
                blocksOuterBindings = node.blocksOuterBindings,
                origin = node.action.title,
                repeatPolicy = node.repeatPolicy,
                presentationOverride = node.presentationOverride,
                onInvoke = node.onInvoke,
            )
        }

    internal fun engineClaims(): List<EngineClaim> =
        claimSnapshot.map { node ->
            EngineClaim(
                sequence = node.sequence,
                enabled = node.enabled,
                blocksOuterClaims = node.blocksOuterClaims,
                repeatPolicy = node.repeatPolicy,
                onInvoke = node.onInvoke,
            )
        }

    /** The innermost registered raw claim matching [event]; raw claims are deliberately local, enabled-only. */
    internal fun rawClaimFor(event: KeyEvent): RawKeyClaimNode? =
        rawClaimSnapshot.lastOrNull { it.enabled && it.matcher(event) }

    /**
     * The host-state contract's focus-loss reset: an armed chord or pending typed suppression must not survive focus
     * leaving the surface, or returning focus would encounter stale pending state.
     */
    override fun onFocusEvent(focusState: FocusState) {
        val hasFocus = focusState.hasFocus
        if (subtreeHadFocus && !hasFocus) state.reset()
        subtreeHadFocus = hasFocus
    }

    private fun observeWindowFocus() {
        var focused = false
        observeReads { focused = currentValueOf(LocalWindowInfo).isWindowFocused }
        // Opening a modal dialog or Alt-Tab/Cmd-Tab triggers a temporary AWT focus loss that ComposeSceneMediator
        // intentionally leaves Compose focus intact across; resetting on window deactivation ensures a held
        // OnceUntilRelease latch or pending chord never survives a missing key-up.
        if (windowWasFocused && !focused) {
            state.reset()
        }
        windowWasFocused = focused
    }

    override fun onObservedReadsChanged() {
        observeWindowFocus()
    }

    override fun onAttach() {
        uiThread = Thread.currentThread()
        ownerComponent =
            try {
                currentValueOf(LocalComponent)
            } catch (_: IllegalStateException) {
                null
            }
        state.attachRoot(this)
        observeWindowFocus()
    }

    override fun onDetach() {
        state.detachRoot(this)
        windowWasFocused = false
        ownerComponent = null
        uiThread = null
    }

    /**
     * Scene-level dispatch, on the preview (tunneling) pass so focused claims keep beating ordinary component input.
     * This is what makes shortcuts observable to plain Compose UI tests: injected key events (`performKeyInput`) reach
     * the host with no window or AWT hook present.
     *
     * Safe next to the production hooks: anything the window hook or the bridge's key-event dispatcher consumes never
     * reaches the scene, and an event they passed re-evaluates to the same pass here without mutating dispatch state.
     * KEY_TYPED suppression stays with the AWT-level hooks — scenes never see typed events.
     */
    override fun onPreKeyEvent(event: KeyEvent): Boolean = state.onPreviewKeyEvent(event)

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        // Cancel an armed two-stroke chord on any pointer press without consuming the pointer event, matching IJPL.
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press) {
            state.cancelPendingChord()
        }
    }

    override fun sharePointerInputWithSiblings(): Boolean = true

    @Suppress("EmptyFunctionBlock") override fun onCancelPointerInput() {}

    public companion object TraverseKey
}

private class ShortcutResolverRootElement(private val state: JewelShortcutHostState) :
    ModifierNodeElement<ShortcutResolverRootNode>() {
    override fun create() = ShortcutResolverRootNode(state)

    override fun update(node: ShortcutResolverRootNode) {
        if (node.state === state) return
        node.state.detachRoot(node)
        node.state = state
        state.attachRoot(node)
        state.presentations.invalidate()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "jewelShortcutResolverRoot"
    }

    override fun equals(other: Any?): Boolean = other is ShortcutResolverRootElement && other.state === state

    override fun hashCode(): Int = state.hashCode()
}

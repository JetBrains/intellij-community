// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.jewel.bridge.actionSystem.PresentationIconDescriptors.resolvedIconDescriptor
import org.jetbrains.jewel.bridge.actionSystem.PresentationIconDescriptors.setResolvedIconDescriptor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.shortcut.ActionPresentation
import org.jetbrains.jewel.foundation.shortcut.ActionPropertyNames
import org.jetbrains.jewel.foundation.shortcut.ActionRegistration
import org.jetbrains.jewel.foundation.shortcut.ActionResolution
import org.jetbrains.jewel.foundation.shortcut.ActionTemplatePresentation
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActionKind
import org.jetbrains.jewel.foundation.shortcut.JewelActionRegistry
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.MenuDismissPolicy
import org.jetbrains.jewel.foundation.shortcut.swingKeyStrokeToJewelKeyStroke
import org.jetbrains.jewel.foundation.util.myLogger

/**
 * The IJPL implementation of [JewelActionRegistry], realizing the attach-or-register lifecycle:
 * - **Startup/declarative (canonical for IDE keyboard shortcuts)**: plugin.xml declares a [JewelActionBridgeAction]
 *   under the Jewel action ID with `<keyboard-shortcut>` entries for its default keys; [register] then *attaches* the
 *   definition to that existing action — it never calls `ActionManager.registerAction` for a declared ID, and closing
 *   the registration never unregisters it.
 * - **Runtime/programmatic**: when no action owns the ID, [register] creates and registers a runtime
 *   [JewelActionBridgeAction]; the returned handle unregisters it when the last reference closes. Scope a handle to a
 *   platform lifetime with [closeWith]. Runtime registration deliberately does not mutate `KeymapManager`, so a
 *   runtime-created action gets no default IDE keymap binding (a warning is logged when `defaultShortcuts` is
 *   non-empty; use `plugin.xml` for IDE-wide defaults, or `ActionUtil.wrap(id).registerCustomShortcutSet(...)` for
 *   component-scoped shortcuts).
 *
 * Identical duplicate definitions are reference-counted; a differing definition for a registered ID fails. An ID owned
 * by a non-Jewel action fails registration — map to existing platform actions explicitly via [JewelActionMappings]
 * instead.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class JewelBridgeActionRegistry(
    private val actionManager: ActionManager,
    /**
     * The panel the Jewel controls live in. Presentations are updated against the focused descendant inside
     * [hostComponent] (falling back to [hostComponent] itself) — the same context [JewelBridgeActionInvoker] executes
     * against — so `update()` and `actionPerformed()` can never disagree about what the action is acting on.
     */
    private val hostComponent: Component?,
) : JewelActionRegistry {
    /** Creates a registry backed by the global [ActionManager] with no panel context. */
    public constructor() : this(ActionManager.getInstance(), null)

    /** Creates a registry backed by [actionManager] with no panel context. */
    public constructor(actionManager: ActionManager) : this(actionManager, null)

    /** Creates a registry backed by the global [ActionManager] bound to [hostComponent]. */
    public constructor(hostComponent: Component?) : this(ActionManager.getInstance(), hostComponent)

    internal constructor(
        actionManager: ActionManager,
        hostComponent: Component?,
        warningSink: (String) -> Unit,
    ) : this(actionManager, hostComponent) {
        this.warningSink = warningSink
    }

    private class Entry(
        val definition: JewelActionDefinition,
        val isRuntimeOwned: Boolean,
        val sharedDeclaredAttachment: SharedDeclaredAttachment? = null,
    ) {
        var refCount: Int = 0
    }

    private class SharedRuntimeEntry(val definition: JewelActionDefinition, val action: JewelActionBridgeAction) {
        var refCount: Int = 0
    }

    private class SharedDeclaredAttachment(
        val actionRef: WeakReference<AnAction>,
        val definition: JewelActionDefinition,
    ) {
        var refCount: Int = 0
    }

    private class AsyncRequestState {
        var generation: Long = 0L
        var promise: CancellablePromise<*>? = null
    }

    private val logger = myLogger()
    private var warningSink: (String) -> Unit = { logger.warn(it) }
    private val reportedSampleFailures = ConcurrentHashMap.newKeySet<JewelActionId>()
    private val entries = ConcurrentHashMap<JewelActionId, Entry>()
    private val asyncRequests = ConcurrentHashMap<JewelActionId, AsyncRequestState>()
    private val readCachedOnly = ThreadLocal<Boolean>()

    /**
     * Definitions synthesised from actions this registry never registered, weakly keyed by live `AnAction` identity so
     * unloading or replacing a dynamic plugin action drops its cached definition and icons automatically.
     */
    @Suppress("DEPRECATION")
    private val adoptedDefinitions: ConcurrentMap<AnAction, ConcurrentHashMap<JewelActionId, JewelActionDefinition>> =
        CollectionFactory.createConcurrentWeakIdentityMap()

    /**
     * Last asynchronously published per-place presentation per live `AnAction` identity + [JewelActionId], weakly keyed
     * by `AnAction` so dynamic plugin unload lets GC collect unloaded actions and their icons cleanly.
     */
    @Suppress("DEPRECATION")
    private val lastPublishedSamples: ConcurrentMap<AnAction, ConcurrentHashMap<JewelActionId, ActionPresentation>> =
        CollectionFactory.createConcurrentWeakIdentityMap()

    /** Invoked on EDT when an async platform presentation update completes so hosts can refresh demand flows. */
    @Volatile internal var onAsyncSampleUpdated: ((JewelActionId) -> Unit)? = null

    internal fun <T> withCachedOnlyRead(block: () -> T): T {
        val previous = readCachedOnly.get()
        readCachedOnly.set(true)
        return try {
            block()
        } finally {
            if (previous == null) readCachedOnly.remove() else readCachedOnly.set(previous)
        }
    }

    internal fun cancelPendingSamples() {
        val promisesToCancel = ArrayList<CancellablePromise<*>>()
        for ((_, req) in asyncRequests) {
            synchronized(req) {
                req.generation++
                req.promise?.let(promisesToCancel::add)
                req.promise = null
            }
        }
        asyncRequests.clear()
        promisesToCancel.forEach(CancellablePromise<*>::cancel)
    }

    @TestOnly
    internal fun awaitPendingSamplesForTest(timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val hasPending = asyncRequests.values.any { req -> synchronized(req) { req.promise != null } }
            if (!hasPending) return
            if (EDT.isCurrentThreadEdt()) {
                UIUtil.dispatchAllInvocationEvents()
            } else {
                try {
                    SwingUtilities.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                } catch (_: java.lang.reflect.InvocationTargetException) {
                    return
                }
            }
            Thread.sleep(10)
        }
    }

    override fun register(definition: JewelActionDefinition): ActionRegistration {
        val id = definition.action.id
        synchronized(sharedRuntimeEntries) {
            val existing = entries[id]
            if (existing != null) {
                check(existing.definition == definition) {
                    "Action '${id.value}' is already registered with a different descriptor"
                }
                val live = actionManager.getAction(id.value)
                if (existing.isRuntimeOwned) {
                    val shared = sharedRuntimeEntries[actionManager]?.get(id)
                    when {
                        live == null && shared != null -> actionManager.registerAction(id.value, shared.action)
                        shared != null && live !== shared.action ->
                            error(
                                "Action ID '${id.value}' is owned by ${live?.javaClass?.name}; " +
                                    "use JewelActionMappings to map to an existing platform action"
                            )
                    }
                } else if (existing.sharedDeclaredAttachment != null) {
                    if (live !== existing.sharedDeclaredAttachment.actionRef.get()) {
                        error(
                            "Action ID '${id.value}' is owned by ${live?.javaClass?.name ?: "no registered action"}; " +
                                "use JewelActionMappings to map to an existing platform action"
                        )
                    }
                }
                existing.refCount++
            } else {
                val entry = createNewRegistrationEntry(id, definition)
                entry.refCount = 1
                entries[id] = entry
            }
        }

        return object : ActionRegistration {
            override val action: JewelAction = definition.action
            private var closed = false

            override fun close() {
                synchronized(sharedRuntimeEntries) {
                    if (closed) return
                    closed = true
                    releaseEntry(id)
                }
            }
        }
    }

    private fun createNewRegistrationEntry(id: JewelActionId, definition: JewelActionDefinition): Entry {
        val sharedForManager = sharedRuntimeEntries[actionManager]
        val shared = sharedForManager?.get(id)
        val declared = actionManager.getAction(id.value)
        if (shared != null) {
            check(shared.definition == definition) {
                "Action '${id.value}' is already registered with a different descriptor"
            }
            return when {
                declared == null -> {
                    actionManager.registerAction(id.value, shared.action)
                    shared.refCount++
                    Entry(definition, isRuntimeOwned = true)
                }
                declared === shared.action -> {
                    shared.refCount++
                    Entry(definition, isRuntimeOwned = true)
                }
                else ->
                    error(
                        "Action ID '${id.value}' is owned by ${declared.javaClass.name}; " +
                            "use JewelActionMappings to map to an existing platform action"
                    )
            }
        }
        return when {
            declared is JewelActionBridgeAction -> {
                val attachment = attachSharedDeclaration(id, declared, definition)
                Entry(definition, isRuntimeOwned = false, sharedDeclaredAttachment = attachment)
            }
            declared == null -> {
                if (definition.defaultShortcuts.isNotEmpty()) {
                    warningSink(
                        "Action '${id.value}' was registered at runtime with " +
                            "${definition.defaultShortcuts.size} default shortcut(s), which are not " +
                            "added to the IDE keymap. Declare JewelActionBridgeAction in plugin.xml " +
                            "with <keyboard-shortcut> for IDE-wide defaults, or bind component-scoped " +
                            "shortcuts via ActionUtil.wrap(\"${id.value}\").registerCustomShortcutSet(...)."
                    )
                }
                val action = JewelActionBridgeAction()
                applyTemplateToRuntimeAction(action, definition.action.template)
                actionManager.registerAction(id.value, action)
                val map =
                    sharedForManager
                        ?: HashMap<JewelActionId, SharedRuntimeEntry>().also {
                            sharedRuntimeEntries[actionManager] = it
                        }
                map[id] = SharedRuntimeEntry(definition, action).apply { refCount = 1 }
                Entry(definition, isRuntimeOwned = true)
            }
            else ->
                error(
                    "Action ID '${id.value}' is owned by ${declared.javaClass.name}; " +
                        "use JewelActionMappings to map to an existing platform action"
                )
        }
    }

    private fun attachSharedDeclaration(
        id: JewelActionId,
        declared: JewelActionBridgeAction,
        definition: JewelActionDefinition,
    ): SharedDeclaredAttachment {
        val attachments =
            sharedDeclaredAttachments.getOrPut(actionManager) { HashMap<JewelActionId, SharedDeclaredAttachment>() }
        val existing = attachments[id]
        if (existing != null && existing.actionRef.get() === declared) {
            check(existing.definition == definition) {
                "Action '${id.value}' is already registered with a different descriptor"
            }
            existing.refCount++
            return existing
        }
        return SharedDeclaredAttachment(WeakReference(declared), definition).apply {
            refCount = 1
            attachments[id] = this
        }
    }

    private fun releaseEntry(id: JewelActionId) {
        val entry = entries[id] ?: return
        entry.refCount--
        if (entry.refCount > 0) return
        entries.remove(id)
        if (entry.isRuntimeOwned) {
            val sharedMap = sharedRuntimeEntries[actionManager]
            val shared = sharedMap?.get(id)
            if (shared != null) {
                shared.refCount--
                if (shared.refCount <= 0) {
                    sharedMap.remove(id)
                    if (sharedMap.isEmpty()) sharedRuntimeEntries.remove(actionManager)
                    if (actionManager.getAction(id.value) === shared.action) {
                        actionManager.unregisterAction(id.value)
                    }
                }
            }
        } else if (entry.sharedDeclaredAttachment != null) {
            val attachment = entry.sharedDeclaredAttachment
            attachment.refCount--
            if (attachment.refCount <= 0) {
                val attachments = sharedDeclaredAttachments[actionManager]
                if (attachments?.get(id) === attachment) {
                    attachments.remove(id)
                    if (attachments.isEmpty()) sharedDeclaredAttachments.remove(actionManager)
                }
            }
        }
    }

    override fun definition(id: JewelActionId): JewelActionDefinition? {
        entries[id]?.let { entry ->
            val live = actionManager.getAction(id.value)
            val valid =
                synchronized(sharedRuntimeEntries) {
                    when {
                        entry.isRuntimeOwned -> {
                            val shared = sharedRuntimeEntries[actionManager]?.get(id)
                            shared != null && (live == null || live === shared.action)
                        }
                        entry.sharedDeclaredAttachment != null ->
                            live === entry.sharedDeclaredAttachment.actionRef.get()
                        else -> true
                    }
                }
            if (valid) return entry.definition
            if (live != null) return null
        }
        val targetId = JewelActionMappings.ideActionIdFor(id) ?: id.value
        val current = actionManager.getAction(targetId)
        synchronized(sharedRuntimeEntries) {
            sharedRuntimeEntries[actionManager]?.get(id)?.let { shared ->
                if (actionManager.getAction(id.value) === shared.action) {
                    return shared.definition
                }
            }
            sharedDeclaredAttachments[actionManager]?.get(id)?.let { attachment ->
                if (current != null && attachment.actionRef.get() === current) {
                    return attachment.definition
                }
            }
        }
        if (current == null) return null
        return adoptedDefinitions
            .computeIfAbsent(current) { ConcurrentHashMap() }
            .computeIfAbsent(id) { adoptDeclaredAction(id, current) }
    }

    override fun definitions(): List<JewelActionDefinition> = entries.values.map { it.definition }

    override fun shortcutsFor(id: JewelActionId): List<JewelKeySequence> {
        if (ApplicationManager.getApplication() == null) return emptyList()
        val targetId = JewelActionMappings.ideActionIdFor(id) ?: id.value
        // Only inspect Swing component hierarchy on EDT; off-EDT callers receive global active-keymap bindings.
        val localShortcuts = if (EDT.isCurrentThreadEdt()) collectComponentShortcuts(targetId) else emptyList()
        val keymapShortcuts = KeymapManager.getInstance()?.activeKeymap?.getShortcuts(targetId).orEmpty()
        return buildList {
                addAll(localShortcuts)
                addAll(keymapShortcuts)
            }
            .mapNotNull { shortcut ->
                val keyboard = shortcut as? KeyboardShortcut ?: return@mapNotNull null
                val first = swingKeyStrokeToJewelKeyStroke(keyboard.firstKeyStroke) ?: return@mapNotNull null
                val second =
                    keyboard.secondKeyStroke?.let { secondStroke ->
                        swingKeyStrokeToJewelKeyStroke(secondStroke) ?: return@mapNotNull null
                    }
                JewelKeySequence(first, second)
            }
            .distinct()
    }

    private class ComponentShortcutCollector(val targetId: String, val targetAction: AnAction?) {
        val collected = ArrayList<Shortcut>()
        val claimedLocalFirstStrokes = HashSet<KeyStroke>()
        val visited = HashSet<Component>()
    }

    private fun collectComponentShortcuts(targetId: String): List<Shortcut> {
        val collector = ComponentShortcutCollector(targetId, actionManager.getAction(targetId))
        collectAncestryShortcuts(hostComponent?.let(::resolveContextComponent), collector)
        collectAncestryShortcuts(hostComponent, collector)
        return collector.collected
    }

    private fun collectAncestryShortcuts(from: Component?, collector: ComponentShortcutCollector) {
        var current = from
        while (current != null && collector.visited.add(current)) {
            val component = current as? JComponent
            if (component != null) {
                collectShortcutsOnComponent(component, collector)
            }
            current = current.parent
        }
    }

    private fun collectShortcutsOnComponent(component: JComponent, collector: ComponentShortcutCollector) {
        val componentFirstStrokes = ArrayList<KeyStroke>()
        for (candidate in ActionUtil.getActions(component)) {
            val unwrapped = ActionUtil.getDelegateChainRootAction(candidate)
            val matches =
                (collector.targetAction != null && unwrapped === collector.targetAction) ||
                    actionManager.getId(unwrapped) == collector.targetId
            for (shortcut in candidate.shortcutSet.shortcuts) {
                val keyboard = shortcut as? KeyboardShortcut ?: continue
                componentFirstStrokes.add(keyboard.firstKeyStroke)
                if (matches && keyboard.firstKeyStroke !in collector.claimedLocalFirstStrokes) {
                    collector.collected.add(keyboard)
                }
            }
        }
        collector.claimedLocalFirstStrokes.addAll(componentFirstStrokes)
    }

    /**
     * Asynchronously updates the platform action for [id] via IJPL's `Utils.expandActionGroupAsync` (honoring both
     * `ActionUpdateThread.EDT` lock policies and `ActionUpdateThread.BGT` threading without ever running a nested EDT
     * loop) and maps the resulting per-place `Presentation` into Jewel's own.
     *
     * Off-EDT callers (and re-entrant [withCachedOnlyRead] reads triggered by [onAsyncSampleUpdated]) return the last
     * published sample for the live action instance without scheduling a new update. On EDT, a new generation-versioned
     * async update is scheduled (cancelling any superseded in-flight request for [id]), and the last published sample
     * (or an initial disabled template-derived sample on first access) is returned synchronously.
     */
    override fun sampledPresentation(id: JewelActionId): ActionPresentation? {
        val targetId = JewelActionMappings.ideActionIdFor(id) ?: id.value
        val declared = actionManager.getAction(targetId) ?: return null
        if (declared is JewelActionBridgeAction) return null // Jewel owns this one; its state comes from the binding.

        val cached = lastPublishedSamples[declared]?.get(id)
        if (!EDT.isCurrentThreadEdt() || readCachedOnly.get() == true) {
            return cached
        }

        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed) {
            return cached
        }

        val contextComponent = hostComponent?.let(::resolveContextComponent)
        val context =
            if (contextComponent != null) {
                DataManager.getInstance().getDataContext(contextComponent)
            } else {
                DataContext.EMPTY_CONTEXT
            }
        scheduleAsyncSample(id, targetId, declared, context, contextComponent)

        return cached ?: initialPendingPresentation(id, targetId, declared, contextComponent)
    }

    private fun scheduleAsyncSample(
        id: JewelActionId,
        targetId: String,
        declared: AnAction,
        context: DataContext,
        contextComponent: Component?,
    ) {
        val req = asyncRequests.computeIfAbsent(id) { AsyncRequestState() }
        val reqGen: Long
        val previousPromise: CancellablePromise<*>?
        synchronized(req) {
            reqGen = ++req.generation
            previousPromise = req.promise
            req.promise = null
        }
        previousPromise?.cancel()

        val declaredRef = WeakReference(declared)
        val contextComponentRef = contextComponent?.let(::WeakReference)
        val initialSelectAllEnabled = isSelectAllEnabledByJewel(id, targetId, contextComponent)
        val freshFactory = PresentationFactory()

        fun cleanupIfCurrent() {
            val shouldRemove =
                synchronized(req) {
                    if (req.generation == reqGen) {
                        req.promise = null
                        true
                    } else {
                        false
                    }
                }
            if (shouldRemove) {
                asyncRequests.remove(id, req)
            }
        }

        val promise =
            @Suppress("TooGenericExceptionCaught", "DEPRECATION")
            try {
                Utils.expandActionGroupAsync(
                    DefaultActionGroup(declared),
                    freshFactory,
                    context,
                    JEWEL_ACTION_PLACE,
                    ActionUiKind.NONE,
                    false,
                )
            } catch (_: ProcessCanceledException) {
                cleanupIfCurrent()
                return
            } catch (_: CancellationException) {
                cleanupIfCurrent()
                return
            } catch (e: Throwable) {
                cleanupIfCurrent()
                logSampleFailure(id, e)
                return
            }

        var settled = false
        var superseded = false
        synchronized(req) {
            if (req.generation == reqGen) {
                req.promise = promise
            } else {
                superseded = true
            }
        }
        if (superseded) {
            promise.cancel()
            return
        }

        promise
            .onSuccess { visibleActions ->
                val liveDeclared = declaredRef.get() ?: return@onSuccess
                val stillCurrent =
                    synchronized(req) { req.generation == reqGen } && actionManager.getAction(targetId) === liveDeclared
                if (!stillCurrent) return@onSuccess
                val selectAllEnabled =
                    if (EDT.isCurrentThreadEdt()) {
                        isSelectAllEnabledByJewel(id, targetId, contextComponentRef?.get())
                    } else {
                        initialSelectAllEnabled
                    }
                val presentation = freshFactory.getPresentation(liveDeclared)
                // If ActionUpdater omitted the action while its presentation remained visible, the child update
                // failed without running to completion; fail closed rather than publishing an untouched template.
                val effectivePresentation =
                    if (liveDeclared !in visibleActions && presentation.isVisible) {
                        presentation.clone().apply { isEnabledAndVisible = false }
                    } else {
                        presentation
                    }
                val asyncResult =
                    buildSampledPresentation(
                        id = id,
                        declared = liveDeclared,
                        presentation = effectivePresentation,
                        selectAllEnabled = selectAllEnabled,
                    )
                lastPublishedSamples.computeIfAbsent(liveDeclared) { ConcurrentHashMap() }[id] = asyncResult
                onAsyncSampleUpdated?.invoke(id)
            }
            .onError { error ->
                if (error !is CancellationException && error !is ProcessCanceledException) {
                    logSampleFailure(id, error)
                }
            }
            .onProcessed {
                settled = true
                cleanupIfCurrent()
            }
        if (settled) {
            cleanupIfCurrent()
        }
    }

    private fun logSampleFailure(id: JewelActionId, error: Throwable) {
        if (reportedSampleFailures.add(id)) {
            logger.warn("Failed to sample presentation for action '${id.value}' (further failures at debug)", error)
        } else {
            logger.debug("Failed to sample presentation for action '${id.value}'", error)
        }
    }

    private fun initialPendingPresentation(
        id: JewelActionId,
        targetId: String,
        declared: AnAction,
        contextComponent: Component?,
    ): ActionPresentation {
        val template = declared.templatePresentation
        return ActionPresentation(
            text = template.text ?: id.value,
            description = template.description,
            // Stay disabled while the initial async platform update is in flight so controls never flash enabled.
            enabled = isSelectAllEnabledByJewel(id, targetId, contextComponent),
            visible = template.isVisible,
            icon = template.resolvedIconDescriptor(),
            menuDismissPolicy = template.keepPopupOnPerform.toJewel(),
            properties = presentationPropertiesOf(template),
            resolution = ActionResolution.Resolved,
        )
    }

    private fun buildSampledPresentation(
        id: JewelActionId,
        declared: AnAction,
        presentation: Presentation,
        selectAllEnabled: Boolean,
    ): ActionPresentation =
        ActionPresentation(
            text = presentation.text ?: declared.templatePresentation.text ?: id.value,
            description = presentation.description,
            enabled = presentation.isEnabled || selectAllEnabled,
            visible = presentation.isVisible,
            icon = presentation.resolvedIconDescriptor(),
            menuDismissPolicy = presentation.keepPopupOnPerform.toJewel(),
            properties = presentationPropertiesOf(presentation),
            resolution = ActionResolution.Resolved,
        )

    private fun presentationPropertiesOf(presentation: Presentation): Map<String, Any> = buildMap {
        if (Toggleable.isSelected(presentation)) put(ActionPropertyNames.Selected, true)
        if (presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR) == true) {
            put(ActionPropertyNames.ShowTextInToolbar, true)
        }
        if (presentation.getClientProperty(ActionUtil.HIDE_DROPDOWN_ICON) == true) {
            put(ActionPropertyNames.HideDropdownIcon, true)
        }
    }

    private fun isSelectAllEnabledByJewel(id: JewelActionId, targetId: String, contextComponent: Component?): Boolean {
        if (id != JewelActions.SelectAll.id || targetId != IdeActions.ACTION_SELECT_ALL) return false
        val surfaceComponent = contextComponent ?: hostComponent
        val host = findJewelShortcutHost(surfaceComponent) ?: return false
        val wrapper = findJewelComposePanelWrapper(surfaceComponent)
        val targetPanel = wrapper?.composePanel
        val allowCrossSurfaceFallback = wrapper?.isPopupWrapper != true
        return host.resolveFocusedHandlerForComponent(
            actionId = JewelActions.SelectAll.id,
            targetComponent = targetPanel,
            allowCrossSurfaceFallback = allowCrossSurfaceFallback,
            excludedComponents = if (allowCrossSurfaceFallback) wrapper?.activePopupPanels().orEmpty() else emptySet(),
        ) != null
    }

    private fun applyTemplateToRuntimeAction(action: JewelActionBridgeAction, template: ActionTemplatePresentation) {
        action.templatePresentation.setText(template.text, false)
        action.templatePresentation.description = template.description
        template.icon?.createSwingIcon()?.let { action.templatePresentation.icon = it }
        action.templatePresentation.setResolvedIconDescriptor(template.icon)
        for ((name, value) in template.properties) {
            if (isReservedBridgeClientProperty(name)) continue
            val key = bridgeClientPropertyKey(name)
            @Suppress("UNCHECKED_CAST") action.templatePresentation.putClientProperty(key as Key<Any>, value)
        }
    }

    /**
     * Builds a [JewelActionDefinition] from an action the IDE already declares, carrying that action's template
     * presentation across: its text, description and icon are what `plugin.xml` (or the action's constructor) authored,
     * so a Jewel control renders a declared action exactly as a Swing one does.
     *
     * The icon crosses as a cross-frontend descriptor via [resolvedIconDescriptor] (reading
     * `Presentation.getIconDescriptor()` when available and falling back to Swing icon conversion), which is what makes
     * one authored icon renderable by both toolkits. Shortcuts stay empty: a declared action's bindings live in the IDE
     * keymap, which the bridge defers to via [shortcutsFor].
     */
    private fun adoptDeclaredAction(id: JewelActionId, declared: AnAction): JewelActionDefinition {
        val template = declared.templatePresentation
        val action =
            JewelAction(
                id = id,
                template =
                    ActionTemplatePresentation(
                        text = template.text ?: id.value,
                        description = template.description,
                        icon = template.resolvedIconDescriptor(),
                    ),
                kind = if (declared is Toggleable) JewelActionKind.Toggle else JewelActionKind.Command,
            )
        return JewelActionDefinition(action)
    }

    private fun KeepPopupOnPerform.toJewel(): MenuDismissPolicy =
        when (this) {
            KeepPopupOnPerform.Never -> MenuDismissPolicy.Dismiss
            KeepPopupOnPerform.IfRequested -> MenuDismissPolicy.KeepIfRequested
            KeepPopupOnPerform.IfPreferred -> MenuDismissPolicy.KeepIfPreferred
            KeepPopupOnPerform.Always -> MenuDismissPolicy.KeepAlways
        }

    private companion object {
        // Process-wide ref counting per ActionManager + action ID so closing one panel keeps runtime actions alive
        // while another panel still holds a registration; WeakHashMap prevents leaking test ActionManager instances.
        private val sharedRuntimeEntries = WeakHashMap<ActionManager, MutableMap<JewelActionId, SharedRuntimeEntry>>()
        private val sharedDeclaredAttachments =
            WeakHashMap<ActionManager, MutableMap<JewelActionId, SharedDeclaredAttachment>>()
    }
}

/**
 * Scopes any [AutoCloseable] — typically an [ActionRegistration] — to a platform [Disposable] lifetime without adding
 * IJPL types to the common registry API. Manual `close()` remains safe: closing is idempotent, so parent disposal
 * merely performs a harmless second close.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun <T : AutoCloseable> T.closeWith(parent: Disposable): T = also { closeable ->
    Disposer.register(parent) { closeable.close() }
}

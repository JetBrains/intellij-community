// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.findFocusedComponent
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.toComposeKeyEvent

/**
 * Resolves the effective [ComposePanel] for a platform [dataContext], routing through the active focusable `JBPopup`
 * wrapper when one is stacked over the owner panel (since `JBPopup` leaves AWT focus on the owner) and supporting both
 * Skiko focus owners and direct [JewelComposePanelWrapper] or [ComposePanel] context components.
 */
internal fun resolveComposePanel(dataContext: DataContext): ComposePanel? {
    val contextComponent = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val wrapper = findJewelComposePanelWrapper(contextComponent)
    if (wrapper != null) {
        return wrapper.topActivePopupWrapper()?.composePanel ?: wrapper.composePanel
    }
    return (contextComponent as? ComposePanel) ?: (contextComponent?.parent as? ComposePanel)
}

/**
 * Tracks the active [AnActionEvent.getInputEvent] across platform action execution so `CopyProvider`/`CutProvider`/
 * `PasteProvider` (which only receive [DataContext]) can enforce `OnceUntilRelease` repeat suppression for both
 * `IdeEventQueue` dispatch and direct `IdeKeyEventDispatcher` calls. Cross-checks against
 * [ActionUtil.getActionThreadContext] so aborted `beforeActionPerformed` or unbalanced listener callbacks do not leak
 * stale key frames into subsequent top-level, non-keyboard, or distinct enclosing actions (two nested unregistered
 * instances of the exact same `AnAction` subclass with the same place and `KEY_PRESSED` share the same class-name
 * action ID).
 */
internal object EditActionInputEventTracker {
    private const val MAX_TRACKED_FRAMES = 64

    // Weak refs ensure an unbalanced beforeActionPerformed frame can never retain AnActionEvent/DataContext/Project or
    // AWT Components past the unwinding of its call frame, while live executing actions stay strongly rooted on stack.
    private class Frame(
        val eventRef: WeakReference<AnActionEvent>,
        val actionId: String,
        val place: String,
        val inputEventId: Int,
        val inputEventRef: WeakReference<InputEvent>?,
    )

    private val installedApps = WeakHashMap<Application, Boolean>()
    private val activeInputEvents = ThreadLocal<ArrayDeque<Frame>>()

    internal fun activeFrameCountForTest(): Int = activeInputEvents.get()?.size ?: 0

    fun ensureInstalled() {
        val app = ApplicationManager.getApplication() ?: return
        if (app.isDisposed) return
        synchronized(installedApps) {
            if (installedApps.containsKey(app)) return
            app.messageBus
                .connect(app)
                .subscribe(
                    AnActionListener.TOPIC,
                    object : AnActionListener {
                        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                            val stack = activeInputEvents.get() ?: ArrayDeque<Frame>().also(activeInputEvents::set)
                            stack.removeAll { it.eventRef.get() == null }
                            while (stack.size >= MAX_TRACKED_FRAMES) {
                                stack.removeFirst()
                            }
                            val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
                            val inputEvent = event.inputEvent
                            stack.addLast(
                                Frame(
                                    eventRef = WeakReference(event),
                                    actionId = actionId,
                                    place = event.place,
                                    inputEventId = inputEvent?.id ?: -1,
                                    inputEventRef = inputEvent?.let(::WeakReference),
                                )
                            )
                        }

                        override fun afterActionPerformed(
                            action: AnAction,
                            event: AnActionEvent,
                            result: AnActionResult,
                        ) {
                            val stack = activeInputEvents.get() ?: return
                            val index = stack.indexOfLast { it.eventRef.get() === event }
                            if (index >= 0) {
                                while (stack.size > index) stack.removeLast()
                            }
                            stack.removeAll { it.eventRef.get() == null }
                            if (stack.isEmpty()) activeInputEvents.remove()
                        }
                    },
                )
            installedApps[app] = true
        }
    }

    fun currentTriggeringKeyEvent(): KeyEvent? {
        val actionContext = ActionUtil.getActionThreadContext() ?: return currentIdeQueueKeyEvent()
        if (actionContext.inputEventId != KeyEvent.KEY_PRESSED) {
            return null
        }
        val stack = activeInputEvents.get()
        if (stack != null) {
            for (index in stack.lastIndex downTo 0) {
                val frame = stack[index]
                val matchesContext =
                    frame.actionId == actionContext.actionId &&
                        frame.place == actionContext.place &&
                        frame.inputEventId == actionContext.inputEventId
                if (matchesContext && frame.eventRef.get() != null) {
                    return frame.inputEventRef?.get() as? KeyEvent
                }
            }
        }
        return currentIdeQueueKeyEvent()
    }

    private fun currentIdeQueueKeyEvent(): KeyEvent? {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed) return null
        return IdeEventQueue.getInstance().trueCurrentEvent as? KeyEvent
    }
}

private fun <T : Function<Boolean>> resolveSemanticsAction(
    dataContext: DataContext,
    key: SemanticsPropertyKey<AccessibilityAction<T>>,
    findFocused: (ComposePanel) -> SemanticsNode?,
): AccessibilityAction<T>? {
    val composePanel = resolveComposePanel(dataContext) ?: return null
    val focused = findFocused(composePanel) ?: return null
    return focused.config.getOrNull(key)
}

private fun resolveSurfaceEditHandler(
    dataContext: DataContext,
    action: JewelAction,
    expectedIdeActionId: String,
): Pair<org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState, () -> Unit>? {
    if (JewelActionMappings.ideActionIdFor(action.id) != expectedIdeActionId) return null
    val host = jewelHostStateFromContext(dataContext) ?: return null
    val targetPanel = resolveComposePanel(dataContext)
    val targetWrapper = findJewelComposePanelWrapper(targetPanel)
    val isPopupSurface = targetWrapper?.isPopupWrapper == true
    val handler =
        host.resolveFocusedHandlerForComponent(
            actionId = action.id,
            targetComponent = targetPanel,
            allowCrossSurfaceFallback = !isPopupSurface,
            excludedComponents = if (isPopupSurface) emptySet() else targetWrapper?.activePopupPanels().orEmpty(),
        ) ?: return null
    return host to handler
}

internal fun isComposeEditActionEnabled(
    dataContext: DataContext,
    action: JewelAction,
    expectedIdeActionId: String,
    semanticsKey: SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>,
    findFocused: (ComposePanel) -> SemanticsNode?,
): Boolean {
    EditActionInputEventTracker.ensureInstalled()
    if (resolveSurfaceEditHandler(dataContext, action, expectedIdeActionId) != null) return true
    return resolveSemanticsAction(dataContext, semanticsKey, findFocused)?.action != null
}

internal fun performComposeEditAction(
    dataContext: DataContext,
    action: JewelAction,
    expectedIdeActionId: String,
    semanticsKey: SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>,
    findFocused: (ComposePanel) -> SemanticsNode?,
) {
    EditActionInputEventTracker.ensureInstalled()
    val resolved = resolveSurfaceEditHandler(dataContext, action, expectedIdeActionId)
    if (resolved != null) {
        val (host, handler) = resolved
        val keyEvent = EditActionInputEventTracker.currentTriggeringKeyEvent()
        if (
            keyEvent?.id == KeyEvent.KEY_PRESSED &&
                host.consumeIfRepeatSuppressed(action.id, keyEvent.toComposeKeyEvent())
        ) {
            return
        }
        handler()
        host.presentations.invalidate()
        return
    }
    resolveSemanticsAction(dataContext, semanticsKey, findFocused)?.action?.invoke()
}

/**
 * Copy/Cut counterparts of [ComposePasteProvider], completing the platform edit-action bridge: focused
 * `Modifier.shortcut(JewelActions.Copy/Cut)` bindings resolve first when the corresponding standard mapping still
 * points to `$Copy`/`$Cut`, falling back to the focused Compose node's text semantics so `$Copy`/`$Cut` work for both
 * custom components (lists, tables) and text fields.
 */
@Internal
@InternalJewelApi
public class ComposeCopyProvider() : CopyProvider {
    private var findFocused: (ComposePanel) -> SemanticsNode? = { it.findFocusedComponent() }

    init {
        EditActionInputEventTracker.ensureInstalled()
    }

    internal constructor(findFocused: (ComposePanel) -> SemanticsNode?) : this() {
        this.findFocused = findFocused
    }

    override fun performCopy(dataContext: DataContext) {
        performComposeEditAction(
            dataContext,
            JewelActions.Copy,
            IdeActions.ACTION_COPY,
            SemanticsActions.CopyText,
            findFocused,
        )
    }

    override fun isCopyEnabled(dataContext: DataContext): Boolean =
        isComposeEditActionEnabled(
            dataContext,
            JewelActions.Copy,
            IdeActions.ACTION_COPY,
            SemanticsActions.CopyText,
            findFocused,
        )

    override fun isCopyVisible(dataContext: DataContext): Boolean = isCopyEnabled(dataContext)

    // This needs to run on the EDT, or we risk synchronization issues like JEWEL-1359
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@Internal
@InternalJewelApi
public class ComposeCutProvider() : CutProvider {
    private var findFocused: (ComposePanel) -> SemanticsNode? = { it.findFocusedComponent() }

    init {
        EditActionInputEventTracker.ensureInstalled()
    }

    internal constructor(findFocused: (ComposePanel) -> SemanticsNode?) : this() {
        this.findFocused = findFocused
    }

    override fun performCut(dataContext: DataContext) {
        performComposeEditAction(
            dataContext,
            JewelActions.Cut,
            IdeActions.ACTION_CUT,
            SemanticsActions.CutText,
            findFocused,
        )
    }

    override fun isCutEnabled(dataContext: DataContext): Boolean =
        isComposeEditActionEnabled(
            dataContext,
            JewelActions.Cut,
            IdeActions.ACTION_CUT,
            SemanticsActions.CutText,
            findFocused,
        )

    override fun isCutVisible(dataContext: DataContext): Boolean = isCutEnabled(dataContext)

    // This needs to run on the EDT, or we risk synchronization issues like JEWEL-1359
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

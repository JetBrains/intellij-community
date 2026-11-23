// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.icons.IconManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.foundation.shortcut.ActionInvocation
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionResolution
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end bridge action dispatch against the real platform action system: a real [ActionManager], a real
 * [KeymapImpl] driving a real [IdeKeyEventDispatcher], and a real Compose composition providing the focused
 * `Modifier.shortcut` bindings — only rendering and AWT focus are stubbed (the Compose surface is headless; a stub
 * [KeyboardFocusManager] plays the role of the skiko focus owner inside the wrapper).
 *
 * Covers registration parity (declared vs runtime), custom-keymap rebinding without recomposition, manual invocation
 * parity, and focused-subtree isolation across two panels — plus the event contract (exactly one Jewel event per
 * bridge-owned invocation).
 */
internal class JewelBridgeActionIntegrationTest {
    private companion object {
        @ClassRule @JvmField val application = ApplicationRule()

        @BeforeClass
        @JvmStatic
        fun activateIconManager() {
            if (runCatching { IconManager.getInstance() }.getOrNull() != null) return
            val manager =
                Class.forName("com.intellij.platform.icons.impl.intellij.IntelliJIconManager")
                    .getDeclaredConstructor()
                    .newInstance() as IconManager
            IconManager.activate(manager)
        }

        const val KEYMAP_NAME = "JewelBridgeActionIntegrationTestKeymap"
        const val ACTION_ID = "test.jewel.integration.action"

        val STROKE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK)
        val REBOUND_STROKE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK)
    }

    @JvmField @Rule internal val compose: ComposeContentTestRule = createComposeRule()

    private class StubFocusManager(private val owner: Component) : DefaultKeyboardFocusManager() {
        override fun getFocusOwner(): Component = owner
    }

    private lateinit var keymap: KeymapImpl
    private lateinit var savedKeymap: Keymap
    private lateinit var savedFocusManager: KeyboardFocusManager
    private lateinit var testDisposable: com.intellij.openapi.Disposable

    /** Stands in for the AWT focus owner that a real focused window would report. */
    private var awtFocusOwner: Component? = null

    private lateinit var wrapperA: JewelComposePanelWrapper
    private lateinit var wrapperB: JewelComposePanelWrapper
    private lateinit var innerA: JTextField
    private lateinit var innerB: JTextField

    private val stateA = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }
    private val stateB = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }

    private val focusA = FocusRequester()
    private val focusB = FocusRequester()

    private var countA = 0
    private var countB = 0
    private var nestedCountA = 0
    private var onInvokeA: (() -> Unit)? = null
    private var overrideTextA by mutableStateOf<String?>(null)
    private val nestedAction = JewelAction(JewelActionId("test.jewel.integration.nested"), "Nested Action")

    private val jewelAction = JewelAction(JewelActionId(ACTION_ID), "Integration Action")

    @Before
    fun setUp() {
        savedFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        testDisposable = Disposer.newDisposable("JewelBridgeActionIntegrationTest")

        // dispatchKeyEvent silently returns false when the DataManager service was never instantiated.
        // In tests that service is a HeadlessDataManager, which never discovers a focused component on
        // its own (there is no focused window); provide the stubbed focus owner as the context component
        // so the bridge action's host resolution sees what production dispatch would see.
        // What production provides through the component-based context: the wrapper in the focus
        // owner's ancestry sinks the host state (pinned by `the wrapper sinks its host state into the
        // data context`); CONTEXT_COMPONENT itself is a UI-snapshot key a headless context never gets.
        val dataManager = DataManager.getInstance() as HeadlessDataManager
        @Suppress("DEPRECATION")
        dataManager.setTestDataProvider(
            { dataId ->
                when {
                    JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> findJewelShortcutHost(awtFocusOwner)
                    PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> awtFocusOwner
                    else -> null
                }
            },
            testDisposable,
        )

        keymap = KeymapImpl()
        keymap.name = KEYMAP_NAME
        KeymapManagerEx.getInstanceEx().schemeManager.addScheme(keymap, false)
        savedKeymap = KeymapManagerEx.getInstanceEx().activeKeymap
        KeymapManagerEx.getInstanceEx().activeKeymap = keymap

        runInEdtAndWait {
            wrapperA = JewelComposePanelWrapper(focusOnClickInside = false)
            wrapperB = JewelComposePanelWrapper(focusOnClickInside = false)
            innerA = JTextField()
            innerB = JTextField()
            wrapperA.composePanel.add(innerA)
            wrapperB.composePanel.add(innerB)
            wrapperA.shortcutHostState = stateA
            wrapperB.shortcutHostState = stateB
        }

        compose.setContent {
            Row {
                Box(stateA.resolverRootModifier) {
                    Box(
                        Modifier.shortcut(nestedAction) { nestedCountA++ }
                            .shortcut(
                                jewelAction,
                                presentation =
                                    overrideTextA?.let { ActionPresentationOverride(text = PresentationValue.Set(it)) }
                                        ?: ActionPresentationOverride.Empty,
                            ) {
                                onInvokeA?.invoke()
                                countA++
                            }
                            .focusRequester(focusA)
                            .focusable()
                    )
                }
                Box(stateB.resolverRootModifier) {
                    Box(Modifier.shortcut(jewelAction) { countB++ }.focusRequester(focusB).focusable())
                }
            }
        }
    }

    @After
    fun tearDown() {
        Disposer.dispose(testDisposable)
        KeyboardFocusManager.setCurrentKeyboardFocusManager(savedFocusManager)
        val actionManager = ActionManager.getInstance()
        if (actionManager.getAction(ACTION_ID) != null) {
            actionManager.unregisterAction(ACTION_ID)
        }
        KeymapManagerEx.getInstanceEx().activeKeymap = savedKeymap
        KeymapManagerEx.getInstanceEx().schemeManager.removeScheme(keymap)
    }

    private fun focusComposeNode(requester: FocusRequester) {
        runBlocking {
            compose.runOnIdle { requester.requestFocus() }
            compose.awaitIdle()
        }
    }

    private fun awtFocusOn(inner: Component) {
        awtFocusOwner = inner
        KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(inner))
    }

    private fun dispatch(source: Component, stroke: KeyStroke): Boolean {
        var dispatched = false
        runInEdtAndWait {
            val event =
                KeyEvent(
                    source,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    stroke.modifiers,
                    stroke.keyCode,
                    KeyEvent.CHAR_UNDEFINED,
                )
            dispatched = IdeKeyEventDispatcher(null).dispatchKeyEvent(event)
        }
        return dispatched
    }

    private fun registerDeclared(): JewelActionBridgeAction {
        val action = JewelActionBridgeAction()
        ActionManager.getInstance().registerAction(ACTION_ID, action)
        keymap.addShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
        return action
    }

    @Test
    fun `declared bridge action dispatches through the IDE keymap to the focused Jewel handler`() {
        registerDeclared()
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        assertTrue(dispatch(innerA, STROKE))
        assertEquals(1, countA)
        assertEquals(0, countB)
    }

    @Test
    fun `runtime-registered bridge action behaves identically to a declared one`() {
        val registry = JewelBridgeActionRegistry()
        val registration = registry.register(JewelActionDefinition(jewelAction))
        keymap.addShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        assertTrue(dispatch(innerA, STROKE))
        assertEquals(1, countA)

        registration.close()
        assertFalse("unregistered action must no longer dispatch", dispatch(innerA, STROKE))
        assertEquals(1, countA)
    }

    @Test
    fun `custom keymap rebinding takes effect without recomposition`() {
        registerDeclared()
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        assertTrue(dispatch(innerA, STROKE))
        assertEquals(1, countA)

        keymap.removeShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
        keymap.addShortcut(ACTION_ID, KeyboardShortcut(REBOUND_STROKE, null))

        assertFalse("unbound stroke must fall through", dispatch(innerA, STROKE))
        assertEquals(1, countA)
        assertTrue(dispatch(innerA, REBOUND_STROKE))
        assertEquals(2, countA)
    }

    @Test
    fun `focused-subtree isolation across two panels follows AWT and Compose focus`() {
        registerDeclared()

        focusComposeNode(focusA)
        awtFocusOn(innerA)
        assertTrue(dispatch(innerA, STROKE))
        assertEquals(1, countA)
        assertEquals(0, countB)

        focusComposeNode(focusB)
        awtFocusOn(innerB)
        assertTrue(dispatch(innerB, STROKE))
        assertEquals(1, countA)
        assertEquals(1, countB)
    }

    @Test
    fun `manual invocation through the bridge invoker matches keyboard dispatch and preserves ActionTrigger Pointer`() {
        registerDeclared()
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        val eventsA = mutableListOf<ActionInvocation>()

        @Suppress("InjectDispatcher") val scope = CoroutineScope(Dispatchers.Unconfined)
        stateA.events.invocations.onEach { eventsA += it }.launchIn(scope)
        try {
            runInEdtAndWait { JewelBridgeActionInvoker(wrapperA).invoke(jewelAction, ActionTrigger.Pointer) }
            // tryToExecute defers the actual perform to the EDT event queue if not invoked synchronously.
            runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

            assertEquals(1, countA)
            assertEquals(0, countB)
            assertEquals(1, eventsA.size)
            assertEquals(ActionTrigger.Pointer, eventsA.single().trigger)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `bridge-owned invocations emit exactly one Jewel event, on the owning host`() {
        registerDeclared()
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        val eventsA = mutableListOf<ActionInvocation>()
        val eventsB = mutableListOf<ActionInvocation>()

        @Suppress("InjectDispatcher") val scope = CoroutineScope(Dispatchers.Unconfined)
        stateA.events.invocations.onEach { eventsA += it }.launchIn(scope)
        stateB.events.invocations.onEach { eventsB += it }.launchIn(scope)
        try {
            assertTrue(dispatch(innerA, STROKE))

            assertEquals(1, eventsA.size)
            assertEquals(JewelActionId(ACTION_ID), eventsA.single().actionId)
            assertTrue(eventsA.single().trigger is ActionTrigger.Keyboard)
            assertTrue(eventsB.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `bridge action declares modal-context enablement`() {
        assertTrue(JewelActionBridgeAction().isEnabledInModalContext)
        assertTrue(DumbAwareJewelActionBridgeAction().isEnabledInModalContext)
    }

    @Test
    fun `the wrapper sinks its host state into the data context`() {
        val sink = TestDataSink()
        wrapperA.uiDataSnapshot(sink)
        assertTrue(sink.get<JewelShortcutHostState>(JEWEL_SHORTCUT_HOST_STATE.name) === stateA)
    }

    @Test
    fun `an IDE-declared action resolves its template presentation through the bridge registry`() {
        // A control bound to an action the IDE already declares must render that action's authored text,
        // description and icon. Before the bridge host carried a registry, the sample had no template at all:
        // controls fell back to the binding's origin text and drew no icon, while still dispatching correctly —
        // a silent, visual-only failure.
        val actionId = "jewel.test.declaredPresentation"
        val declared =
            object : AnAction("Declared Save", "Saves the declared way", AllIcons.Actions.MenuSaveall) {
                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, declared)
        try {
            val registry = JewelBridgeActionRegistry(actionManager)

            val definition = registry.definition(JewelActionId(actionId))

            assertNotNull("the declared action should be adopted", definition)
            val template = definition!!.action.template
            assertEquals("Declared Save", template.text)
            assertEquals("Saves the declared way", template.description)
            assertNotNull("the platform icon should cross as a descriptor", template.icon)
        } finally {
            actionManager.unregisterAction(actionId)
        }
    }

    @Test
    fun `an unknown action ID is not adopted and does not resolve`() {
        val registry = JewelBridgeActionRegistry(ActionManager.getInstance())
        assertNull(registry.definition(JewelActionId("jewel.test.doesNotExistAnywhere")))
    }

    @Test
    fun `mapped standard actions resolve through JewelActionMappings in definition and sampledPresentation`() {
        val actionManager = ActionManager.getInstance()
        val copyAction = actionManager.getAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY)
        assertNotNull("platform Copy action must exist", copyAction)

        JewelActionMappings.clearForTests()
        try {
            val registry = JewelBridgeActionRegistry(actionManager)
            assertNull(registry.definition(JewelActions.Copy.id))

            JewelActionMappings.installStandardMappings()
            val definition = registry.definition(JewelActions.Copy.id)
            assertNotNull("mapped standard action should adopt after mappings install", definition)
            assertEquals(copyAction!!.templatePresentation.text, definition!!.action.template.text)

            try {
                var sampled: org.jetbrains.jewel.foundation.shortcut.ActionPresentation? = null
                runInEdtAndWait { sampled = registry.sampledPresentation(JewelActions.Copy.id) }
                assertNotNull("mapped standard action should return initial sample synchronously", sampled)
                assertEquals(ActionResolution.Resolved, sampled!!.resolution)

                registry.awaitPendingSamplesForTest()
                val settled = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActions.Copy.id) }
                assertNotNull("mapped standard action should publish async platform presentation", settled)
                assertEquals(ActionResolution.Resolved, settled!!.resolution)
            } finally {
                registry.cancelPendingSamples()
            }
        } finally {
            JewelActionMappings.installStandardMappings()
        }
    }

    @Test
    fun `sampledPresentation never runs update off EDT and returns the last EDT-published sample off EDT`() {
        val actionId = "jewel.test.offEdtSampled"
        var updateCount = 0
        var updatedOnEdt = false
        val declared =
            object : AnAction("Off EDT", "Safe off EDT", AllIcons.Actions.MenuSaveall) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    updateCount++
                    updatedOnEdt = EDT.isCurrentThreadEdt()
                    e.presentation.isEnabled = true
                    e.presentation.text = "Updated On EDT"
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, declared)
        val registry = JewelBridgeActionRegistry(actionManager, wrapperA)
        try {
            fun sampleOffEdt(): Pair<org.jetbrains.jewel.foundation.shortcut.ActionPresentation?, Throwable?> {
                var result: org.jetbrains.jewel.foundation.shortcut.ActionPresentation? = null
                var error: Throwable? = null
                val thread = Thread {
                    try {
                        result = registry.sampledPresentation(JewelActionId(actionId))
                    } catch (t: Throwable) {
                        error = t
                    }
                }
                thread.start()
                thread.join(10_000)
                return result to error
            }

            // Before any EDT sample: off-EDT must NOT execute update() and returns null cleanly.
            val (beforeEdt, beforeError) = sampleOffEdt()
            assertNull("off-EDT sampledPresentation must not throw: $beforeError", beforeError)
            assertNull("off-EDT call before first EDT sample must return null", beforeEdt)
            assertEquals("update() must never execute off EDT", 0, updateCount)

            // Sample once on EDT (schedules async update and returns initial pending disabled sample synchronously).
            var initialPending: org.jetbrains.jewel.foundation.shortcut.ActionPresentation? = null
            runInEdtAndWait { initialPending = registry.sampledPresentation(JewelActionId(actionId)) }
            assertNotNull(initialPending)
            assertFalse(
                "initial pending sample must stay disabled until async update completes",
                initialPending!!.enabled,
            )

            registry.awaitPendingSamplesForTest()
            val edtResult = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertNotNull(edtResult)
            assertEquals("Updated On EDT", edtResult!!.text)
            assertTrue(edtResult.enabled)
            assertTrue("EDT action update must run on EDT", updatedOnEdt)
            assertEquals(1, updateCount)

            // Subsequent off-EDT call returns the cached EDT sample without re-running update().
            val (afterEdt, afterError) = sampleOffEdt()
            assertNull("off-EDT sampledPresentation must not throw: $afterError", afterError)
            assertEquals(edtResult, afterEdt)
            assertEquals("cached off-EDT sample must not re-run update()", 1, updateCount)
        } finally {
            registry.cancelPendingSamples()
            actionManager.unregisterAction(actionId)
        }
    }

    @Test
    fun `registry shortcutsFor reads active IDE keymap shortcuts for Jewel and mapped actions`() {
        keymap.addShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
        val actionId = JewelActionId(ACTION_ID)
        val registry = JewelBridgeActionRegistry(ActionManager.getInstance(), wrapperA)
        lateinit var shortcuts: List<org.jetbrains.jewel.foundation.shortcut.JewelKeySequence>
        runInEdtAndWait { shortcuts = registry.shortcutsFor(actionId) }

        assertEquals(1, shortcuts.size)
        assertEquals(
            org.jetbrains.jewel.foundation.shortcut.JewelKeySequence(
                org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke(
                    androidx.compose.ui.input.key.Key(KeyEvent.VK_LEFT),
                    alt = true,
                )
            ),
            shortcuts.single(),
        )

        val host = JewelShortcutHostState(registry) { InMemoryJewelKeymap("bridge-empty") }
        lateinit var hostShortcuts: List<org.jetbrains.jewel.foundation.shortcut.JewelKeySequence>
        runInEdtAndWait { hostShortcuts = host.shortcutsFor(actionId) }
        assertEquals(shortcuts, hostShortcuts)
    }

    @Test
    fun `bridge action update disables mnemonic parsing for ampersand and underscore`() {
        val bridgeAction = registerDeclared()
        val mnemonicTitle = "Find & Replace_Literal"
        overrideTextA = mnemonicTitle
        compose.waitForIdle()
        focusComposeNode(focusA)
        awtFocusOn(innerA)

        val presentation = com.intellij.openapi.actionSystem.Presentation()
        runInEdtAndWait {
            val event =
                AnActionEvent.createEvent(
                    bridgeAction,
                    DataManager.getInstance().getDataContext(innerA),
                    presentation,
                    JEWEL_ACTION_PLACE,
                    com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                    null,
                )
            bridgeAction.update(event)
        }
        assertEquals(mnemonicTitle, presentation.text)
        assertEquals(-1, presentation.displayedMnemonicIndex)
        assertTrue(presentation.isEnabled)
    }

    @Test
    fun `pointer trigger clears thread-local before handler runs so nested key-event action emits Keyboard`() {
        registerDeclared()
        val actionManager = ActionManager.getInstance()
        val nestedBridgeAction = JewelActionBridgeAction()
        actionManager.registerAction(nestedAction.id.value, nestedBridgeAction)
        val nestedStroke = KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK)
        keymap.addShortcut(nestedAction.id.value, KeyboardShortcut(nestedStroke, null))

        focusComposeNode(focusA)
        awtFocusOn(innerA)

        val eventsA = mutableListOf<ActionInvocation>()

        @Suppress("InjectDispatcher") val scope = CoroutineScope(Dispatchers.Unconfined)
        stateA.events.invocations.onEach { eventsA += it }.launchIn(scope)
        var triggerVisibleInsideHandler: PendingBridgeTrigger? = PendingBridgeTrigger("sentinel", ActionTrigger.Pointer)
        onInvokeA = {
            triggerVisibleInsideHandler = bridgeInvokerTrigger.get()
            assertTrue(dispatch(innerA, nestedStroke))
        }
        try {
            runInEdtAndWait { JewelBridgeActionInvoker(wrapperA).invoke(jewelAction, ActionTrigger.Pointer) }
            runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

            assertNull(
                "bridgeInvokerTrigger must be cleared before handler runs so nested event loops stay isolated",
                triggerVisibleInsideHandler,
            )
            assertEquals(1, countA)
            assertEquals(1, nestedCountA)
            assertEquals(2, eventsA.size)
            assertEquals(ActionInvocation(nestedAction.id, null, ActionTrigger.Keyboard(null)), eventsA[0])
            assertEquals(ActionInvocation(jewelAction.id, null, ActionTrigger.Pointer), eventsA[1])
        } finally {
            onInvokeA = null
            scope.cancel()
            actionManager.unregisterAction(nestedAction.id.value)
        }
    }
}

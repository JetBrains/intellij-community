// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.installBridgeClaimLane
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchResult
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionPropertyNames
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.ShortcutRepeatPolicy
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Platform integration tests for dumb-mode `SelectAll` and ancestor shortcut preservation, direct `update()` visibility
 * and reserved client-property guards, and `JewelActionPromoter` suppression across wrapped/dumb-mode actions.
 */
internal class JewelBridgeActionPromoterAndPresentationIntegrationTest {
    private companion object {
        @ClassRule @JvmField val projectRule = ProjectRule()

        const val KEYMAP_NAME = "JewelBridgeActionPromoterTestKeymap"
        const val OUTER_ID = "test.jewel.promoter.outer"
        const val INNER_ID = "test.jewel.promoter.inner"
    }

    @JvmField @Rule internal val compose: ComposeContentTestRule = createComposeRule()

    private class StubFocusManager(private val owner: Component) : DefaultKeyboardFocusManager() {
        override fun getFocusOwner(): Component = owner
    }

    private lateinit var keymap: KeymapImpl
    private lateinit var savedKeymap: Keymap
    private lateinit var savedFocusManager: KeyboardFocusManager
    private lateinit var testDisposable: Disposable

    private var awtFocusOwner: Component? = null
    private lateinit var parentPanel: JPanel
    private lateinit var wrapper: JewelComposePanelWrapper
    private lateinit var inner: JTextField

    private val state = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }
    private val focusNode = FocusRequester()

    private val outerAction = JewelAction(JewelActionId(OUTER_ID), "Outer Action")
    private val innerAction = JewelAction(JewelActionId(INNER_ID), "Inner Action")

    private var outerCount = 0
    private var innerCount = 0
    private var selectAllCount = 0
    private var copyCount = 0
    private var cutCount = 0
    private var pasteCount = 0

    private var selectAllEnabled by mutableStateOf(false)
    private var selectAllRepeatPolicy by mutableStateOf(ShortcutRepeatPolicy.RepeatWhileHeld)
    private var editActionsEnabled by mutableStateOf(false)
    private var innerPresentationOverride by mutableStateOf(ActionPresentationOverride.Empty)

    @Before
    fun setUp() {
        savedFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        JewelActionMappings.installStandardMappings()
        testDisposable = Disposer.newDisposable("JewelBridgeActionPromoterAndPresentationIntegrationTest")
        val dataManager = DataManager.getInstance() as HeadlessDataManager
        @Suppress("DEPRECATION")
        dataManager.setTestDataProvider(
            { dataId ->
                when {
                    CommonDataKeys.PROJECT.`is`(dataId) -> projectRule.project
                    JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> findJewelShortcutHost(awtFocusOwner)
                    PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> awtFocusOwner
                    PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> ComposeCopyProvider()
                    PlatformDataKeys.CUT_PROVIDER.`is`(dataId) -> ComposeCutProvider()
                    PlatformDataKeys.PASTE_PROVIDER.`is`(dataId) -> ComposePasteProvider()
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
            parentPanel = JPanel()
            wrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            inner = JTextField()
            wrapper.composePanel.add(inner)
            parentPanel.add(wrapper)
            wrapper.shortcutHostState = state
        }

        compose.setContent {
            Box(
                state.resolverRootModifier
                    .shortcut(outerAction) { outerCount++ }
                    .shortcut(
                        JewelActions.SelectAll,
                        enabled = selectAllEnabled,
                        repeatPolicy = selectAllRepeatPolicy,
                    ) {
                        selectAllCount++
                    }
            ) {
                Box(
                    Modifier.shortcut(innerAction, presentation = innerPresentationOverride) { innerCount++ }
                        .shortcut(
                            JewelActions.Copy,
                            enabled = editActionsEnabled,
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            copyCount++
                        }
                        .shortcut(
                            JewelActions.Cut,
                            enabled = editActionsEnabled,
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            cutCount++
                        }
                        .shortcut(
                            JewelActions.Paste,
                            enabled = editActionsEnabled,
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            pasteCount++
                        }
                        .focusRequester(focusNode)
                        .focusable()
                )
            }
        }
    }

    @After
    fun tearDown() {
        Disposer.dispose(testDisposable)
        KeyboardFocusManager.setCurrentKeyboardFocusManager(savedFocusManager)
        val actionManager = ActionManager.getInstance()
        for (id in listOf(OUTER_ID, INNER_ID)) {
            if (actionManager.getAction(id) != null) actionManager.unregisterAction(id)
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

    private fun awtFocusOn(component: Component) {
        awtFocusOwner = component
        KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(component))
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

    private fun keyPressed(keyCode: Int, modifiers: Int = InputEvent.CTRL_DOWN_MASK): KeyEvent =
        KeyEvent(inner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)

    private fun releaseKey(stroke: KeyStroke) {
        runInEdtAndWait {
            val release =
                KeyEvent(
                    inner,
                    KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(),
                    stroke.modifiers,
                    stroke.keyCode,
                    KeyEvent.CHAR_UNDEFINED,
                )
            KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(release)
        }
    }

    private fun updateAction(
        action: AnAction,
        context: DataContext,
        presentation: Presentation,
        place: String = ActionPlaces.MAIN_MENU,
    ) {
        runInEdtAndWait {
            val event = AnActionEvent.createEvent(action, context, presentation, place, ActionUiKind.NONE, null)
            ActionUtil.updateAction(action, event)
        }
    }

    private fun directUpdate(action: AnAction, component: Component, presentation: Presentation) {
        runInEdtAndWait {
            val event =
                AnActionEvent.createEvent(
                    action,
                    DataManager.getInstance().getDataContext(component),
                    presentation,
                    ActionPlaces.TOOLBAR,
                    ActionUiKind.TOOLBAR,
                    null,
                )
            action.update(event)
        }
    }

    @Test
    fun `SelectAll bridge works in dumb mode and never shadows parent Swing Ctrl+A when no SelectAll binding is focused`() {
        JewelActionMappings.installStandardMappings()
        val selectAllStroke = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_SELECT_ALL, KeyboardShortcut(selectAllStroke, null))

        var parentHits = 0
        val parentSelectAll =
            object : AnAction(), DumbAware {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun actionPerformed(e: AnActionEvent) {
                    parentHits++
                }
            }
        parentSelectAll.registerCustomShortcutSet(CustomShortcutSet(selectAllStroke), parentPanel, testDisposable)

        focusComposeNode(focusNode)
        awtFocusOn(inner)
        val removeLane = installBridgeClaimLane(wrapper, state)
        try {
            val bridgeSelectAll = wrapper.selectAllBridgeAction
            assertNotNull(bridgeSelectAll)
            assertTrue("JewelSelectAllBridgeAction must be DumbAware", bridgeSelectAll!!.isDumbAware)
            assertFalse(
                "JewelSelectAllBridgeAction must carry non-blank template text",
                bridgeSelectAll.templatePresentation.text.isNullOrBlank(),
            )

            // No Compose SelectAll binding focused: Swing parent's Ctrl+A must NOT be shadowed by wrapper.
            selectAllEnabled = false
            compose.waitForIdle()
            assertTrue(
                "parent Swing Ctrl+A must run when Compose has no SelectAll binding",
                dispatch(inner, selectAllStroke),
            )
            assertEquals(1, parentHits)
            assertEquals(0, selectAllCount)

            // Focused Compose SelectAll binding runs during dumb mode via both keyboard and invoker.
            selectAllEnabled = true
            compose.waitForIdle()
            val registry = JewelBridgeActionRegistry(ActionManager.getInstance(), wrapper)
            DumbModeTestUtils.runInDumbModeSynchronously(
                projectRule.project,
                ThrowableRunnable {
                    assertTrue("SelectAll shortcut must dispatch during dumb mode", dispatch(inner, selectAllStroke))
                    assertEquals(1, selectAllCount)
                    assertEquals(1, parentHits)

                    var sampledEnabled = false
                    var invokerResult: ActionDispatchResult? = null
                    try {
                        // Await async sampling before tryToExecute (which cancels in-flight ActionUpdater sessions).
                        runInEdtAndWait { registry.sampledPresentation(JewelActions.SelectAll.id) }
                        registry.awaitPendingSamplesForTest()
                        sampledEnabled =
                            registry.withCachedOnlyRead {
                                registry.sampledPresentation(JewelActions.SelectAll.id)?.enabled
                            } == true
                        runInEdtAndWait {
                            invokerResult =
                                JewelBridgeActionInvoker(wrapper).invoke(JewelActions.SelectAll, ActionTrigger.Pointer)
                            UIUtil.dispatchAllInvocationEvents()
                        }
                    } finally {
                        registry.cancelPendingSamples()
                    }
                    assertTrue("sampledPresentation must agree with dumb-mode enablement", sampledEnabled)
                    assertEquals(ActionDispatchResult.Dispatched, invokerResult)
                    assertEquals(2, selectAllCount)
                },
            )
        } finally {
            removeLane()
        }
    }

    @Test
    fun `direct update protects CustomComponentAction COMPONENT_KEY and WRITTEN_PROPERTIES_KEY and resets isVisible`() {
        val bridgeAction = JewelActionBridgeAction()
        ActionManager.getInstance().registerAction(INNER_ID, bridgeAction)
        innerPresentationOverride =
            ActionPresentationOverride(
                visible = PresentationValue.Set(false),
                properties =
                    mapOf(
                        CustomComponentAction.COMPONENT_KEY.toString() to "attempt-clobber",
                        "org.jetbrains.jewel.bridge.actionSystem.writtenProperties" to setOf("attempt-clobber"),
                        ActionPropertyNames.ShowTextInToolbar to true,
                    ),
            )
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        val customComponent = JLabel("toolbar-widget")
        val presentation =
            Presentation().apply {
                copyFrom(bridgeAction.templatePresentation)
                putClientProperty(CustomComponentAction.COMPONENT_KEY, customComponent)
            }

        directUpdate(bridgeAction, inner, presentation)
        assertFalse("override must hide presentation while focused", presentation.isVisible)
        assertEquals(true, presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))
        assertSame(
            "CustomComponentAction.COMPONENT_KEY must never be overwritten by string property name",
            customComponent,
            presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY),
        )

        val outside = JTextField()
        awtFocusOn(outside)
        directUpdate(bridgeAction, outside, presentation)
        assertTrue("direct update after blur must restore template isVisible", presentation.isVisible)
        assertFalse(presentation.isEnabled)
        assertNull(presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))
        assertSame(
            "CustomComponentAction.COMPONENT_KEY must remain intact after reset",
            customComponent,
            presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY),
        )
    }

    @Test
    fun `JewelActionPromoter suppresses wrapped outer actions, outer SelectAll, and respects isDumbAware in dumb mode`() {
        val actionManager = ActionManager.getInstance()
        val dumbOuter = DumbAwareJewelActionBridgeAction()
        val nonDumbInner = JewelActionBridgeAction()
        actionManager.registerAction(OUTER_ID, dumbOuter)
        actionManager.registerAction(INNER_ID, nonDumbInner)
        selectAllEnabled = true
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        val outerWrap = ActionUtil.wrap(OUTER_ID)
        val innerWrap = ActionUtil.wrap(INNER_ID)
        val promoter = JewelActionPromoter()
        val removeLane = installBridgeClaimLane(wrapper, state)
        try {
            val dataContext = DataManager.getInstance().getDataContext(inner)

            // 1) Two ActionUtil.wrap actions: JewelActionPromoter suppresses outer wrapper.
            assertEquals(listOf(outerWrap), promoter.suppress(listOf(outerWrap, innerWrap), dataContext))

            // 2) Outer ActionUtil.wrap + inner JewelActionBridgeAction: both bridge action and promoter suppress outer.
            assertEquals(listOf(outerWrap), nonDumbInner.suppress(listOf(outerWrap, nonDumbInner), dataContext))
            assertEquals(listOf(outerWrap), promoter.suppress(listOf(outerWrap, nonDumbInner), dataContext))

            // 3) Outer JewelSelectAllBridgeAction + inner JewelActionBridgeAction: inner suppresses outer SelectAll.
            val selectAllBridge = wrapper.selectAllBridgeAction!!
            assertEquals(listOf(selectAllBridge), promoter.suppress(listOf(selectAllBridge, nonDumbInner), dataContext))

            // 4) Real dumb mode: non-DumbAware inner action (raw and wrapped) is skipped so DumbAware outer is NOT
            //    suppressed.
            DumbModeTestUtils.runInDumbModeSynchronously(
                projectRule.project,
                ThrowableRunnable {
                    assertNull(
                        "non-DumbAware inner bridge must not suppress DumbAware outer in dumb mode",
                        dumbOuter.suppress(listOf(dumbOuter, nonDumbInner), dataContext),
                    )
                    assertNull(
                        "wrapped non-DumbAware inner must not suppress wrapped DumbAware outer in dumb mode",
                        promoter.suppress(listOf(outerWrap, innerWrap), dataContext),
                    )
                },
            )
        } finally {
            removeLane()
        }
    }

    @Test
    fun `place-specific override-text survives update when unresolved, inherited, and after a focused override blurs`() {
        val bridgeAction = JewelActionBridgeAction()
        bridgeAction.templatePresentation.setText(innerAction.title, false)
        bridgeAction.addTextOverride(ActionPlaces.MAIN_MENU, "Main menu text")
        ActionManager.getInstance().registerAction(INNER_ID, bridgeAction)

        // 1) Unresolved (empty data context): ActionUtil.updateAction keeps place-specific <override-text>.
        val presentation = bridgeAction.templatePresentation.clone()
        updateAction(bridgeAction, DataContext.EMPTY_CONTEXT, presentation)
        assertFalse("action must be disabled without a Jewel host", presentation.isEnabled)
        assertEquals("Main menu text", presentation.text)

        // 2) Focused binding with Inherit text keeps place-specific <override-text>.
        innerPresentationOverride = ActionPresentationOverride.Empty
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(inner), presentation)
        assertTrue(presentation.isEnabled)
        assertEquals("Main menu text", presentation.text)

        // 3) Focused binding with explicit text override applies "Override text".
        innerPresentationOverride = ActionPresentationOverride(text = PresentationValue.Set("Override text"))
        compose.waitForIdle()
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(inner), presentation)
        assertTrue(presentation.isEnabled)
        assertEquals("Override text", presentation.text)

        // 4) Focus leaves: place-specific <override-text> ("Main menu text") is restored, not template text.
        val outside = JTextField()
        awtFocusOn(outside)
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(outside), presentation)
        assertFalse(presentation.isEnabled)
        assertEquals("Main menu text", presentation.text)
    }

    @Test
    fun `ideLafBridge module descriptor declares JewelActionPromoter which suppresses outer wrapped actions in real dispatch`() {
        val descriptorStream =
            JewelActionBridgeAction::class
                .java
                .classLoader
                .getResourceAsStream("intellij.platform.jewel.ideLafBridge.xml")
        assertNotNull("intellij.platform.jewel.ideLafBridge.xml must be packaged on classpath", descriptorStream)
        val promoterImpl =
            descriptorStream!!.use { stream ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
                val nodes = doc.getElementsByTagName("actionPromoter")
                (0 until nodes.length)
                    .map { nodes.item(it).attributes.getNamedItem("implementation")?.nodeValue }
                    .firstOrNull { it == JewelActionPromoter::class.java.name }
            }
        assertEquals(
            "intellij.platform.jewel.ideLafBridge.xml must declare JewelActionPromoter",
            JewelActionPromoter::class.java.name,
            promoterImpl,
        )

        // ideLafBridge-tests_test does not bundle platform/compose/plugin (the plugin that declares
        // <content><module name="intellij.platform.jewel.ideLafBridge"/></content>), so load the exact
        // <actionPromoter> entry extracted from intellij.platform.jewel.ideLafBridge.xml via DynamicPlugins and verify
        // real IdeKeyEventDispatcher dispatch across two ActionUtil.wrap(...) actions sharing one shortcut.
        val pluginsDir = Files.createTempDirectory("jewel-action-promoter-ep-test")
        lateinit var pluginDisposable: Disposable
        runInEdtAndWait {
            pluginDisposable =
                loadPluginWithText(
                    plugin("org.jetbrains.jewel.test.action.promoter") {
                        name = "Jewel ActionPromoter EP Test"
                        depends("com.intellij.modules.platform")
                        extensions("""<actionPromoter implementation="$promoterImpl"/>""")
                    },
                    pluginsDir,
                )
        }
        val actionManager = ActionManager.getInstance()
        val outerBridge = JewelActionBridgeAction()
        val innerBridge = JewelActionBridgeAction()
        actionManager.registerAction(OUTER_ID, outerBridge)
        actionManager.registerAction(INNER_ID, innerBridge)
        val stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.ALT_DOWN_MASK)
        // Register outer wrapper FIRST so registration order alone would run outer without JewelActionPromoter.
        val outerWrap = ActionUtil.wrap(OUTER_ID)
        val innerWrap = ActionUtil.wrap(INNER_ID)
        outerWrap.registerCustomShortcutSet(CustomShortcutSet(stroke), wrapper, testDisposable)
        innerWrap.registerCustomShortcutSet(CustomShortcutSet(stroke), wrapper, testDisposable)
        try {
            assertTrue(
                "com.intellij.actionPromoter must contain JewelActionPromoter after loading descriptor extension",
                ActionPromoter.EP_NAME.extensionList.any { it is JewelActionPromoter },
            )
            focusComposeNode(focusNode)
            awtFocusOn(inner)

            assertTrue("wrapped inner action must dispatch via IdeKeyEventDispatcher", dispatch(inner, stroke))
            assertEquals("outer wrapped action must be suppressed by JewelActionPromoter EP", 0, outerCount)
            assertEquals("inner wrapped action must execute", 1, innerCount)
        } finally {
            outerWrap.unregisterCustomShortcutSet(wrapper)
            innerWrap.unregisterCustomShortcutSet(wrapper)
            runInEdtAndWait { Disposer.dispose(pluginDisposable) }
            pluginsDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `mapped Copy, Cut, and Paste enforce OnceUntilRelease on held auto-repeat and re-arm on KEY_RELEASED`() {
        JewelActionMappings.installStandardMappings()
        editActionsEnabled = true
        compose.waitForIdle()
        val ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
        val ctrlX = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
        val ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_COPY, KeyboardShortcut(ctrlC, null))
        keymap.addShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(ctrlX, null))
        keymap.addShortcut(IdeActions.ACTION_PASTE, KeyboardShortcut(ctrlV, null))
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        // Direct IdeKeyEventDispatcher without installBridgeClaimLane: held auto-repeat runs Copy only once, while
        // programmatic invocation still runs cleanly.
        assertTrue(dispatch(inner, ctrlC))
        assertTrue(dispatch(inner, ctrlC))
        assertEquals("held Ctrl+C must run OnceUntilRelease Copy handler once", 1, copyCount)
        runInEdtAndWait {
            assertEquals(
                ActionDispatchResult.Dispatched,
                JewelBridgeActionInvoker(wrapper).invoke(JewelActions.Copy, ActionTrigger.Pointer),
            )
            UIUtil.dispatchAllInvocationEvents()
        }
        assertEquals("programmatic Copy invocation must not be suppressed by keyboard repeat latch", 2, copyCount)

        // Full lane: Copy, Cut, Paste, and SelectAll all suppress auto-repeat and re-arm on KEY_RELEASED.
        selectAllEnabled = true
        selectAllRepeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease
        compose.waitForIdle()
        val ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_SELECT_ALL, KeyboardShortcut(ctrlA, null))
        runInEdtAndWait { state.reset() }
        val removeLane = installBridgeClaimLane(wrapper, state)
        try {
            assertTrue(dispatch(inner, ctrlC))
            assertTrue(dispatch(inner, ctrlC))
            assertEquals(3, copyCount)
            releaseKey(ctrlC)
            assertTrue(dispatch(inner, ctrlC))
            assertEquals("Copy must re-arm on KEY_RELEASED", 4, copyCount)

            assertTrue(dispatch(inner, ctrlX))
            assertTrue(dispatch(inner, ctrlX))
            assertEquals("held Ctrl+X must run OnceUntilRelease Cut handler once", 1, cutCount)
            releaseKey(ctrlX)
            assertTrue(dispatch(inner, ctrlX))
            assertEquals("Cut must re-arm on KEY_RELEASED", 2, cutCount)

            assertTrue(dispatch(inner, ctrlV))
            assertTrue(dispatch(inner, ctrlV))
            assertEquals("held Ctrl+V must run OnceUntilRelease Paste handler once", 1, pasteCount)
            releaseKey(ctrlV)
            assertTrue(dispatch(inner, ctrlV))
            assertEquals("Paste must re-arm on KEY_RELEASED", 2, pasteCount)

            assertTrue(dispatch(inner, ctrlA))
            assertTrue(dispatch(inner, ctrlA))
            assertEquals("held Ctrl+A must run OnceUntilRelease SelectAll handler once", 1, selectAllCount)
            releaseKey(ctrlA)
            assertTrue(dispatch(inner, ctrlA))
            assertEquals("SelectAll must re-arm on KEY_RELEASED", 2, selectAllCount)
        } finally {
            removeLane()
        }
    }

    @Test
    fun `failed or unbalanced beforeActionPerformed never leaks a stale key frame to later programmatic or non-key actions`() {
        editActionsEnabled = true
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        // Install tracker before registering the failing listener so tracker pushes first.
        ComposeCopyProvider()

        val failingAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val listenerDisposable = Disposer.newDisposable(testDisposable, "failing listener")
        ApplicationManager.getApplication()
            .messageBus
            .connect(listenerDisposable)
            .subscribe(
                AnActionListener.TOPIC,
                object : AnActionListener {
                    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                        if (action === failingAction) {
                            error("simulated AnActionListener failure")
                        }
                    }
                },
            )

        val ctrlC = keyPressed(KeyEvent.VK_C)
        var reportedAfterFailure: KeyEvent? = ctrlC
        var reportedInsideNonKeyAction: KeyEvent? = ctrlC
        var unbalancedFramesBeforeNonKey = 0
        runInEdtAndWait {
            val context = DataManager.getInstance().getDataContext(inner)
            val keyActionEvent =
                AnActionEvent.createEvent(
                    failingAction,
                    context,
                    null,
                    ActionPlaces.KEYBOARD_SHORTCUT,
                    ActionUiKind.NONE,
                    ctrlC,
                )
            @Suppress("TooGenericExceptionCaught")
            try {
                ActionUtil.performAction(failingAction, keyActionEvent)
            } catch (_: Throwable) {
                // Expected when message bus error propagation is enabled.
            }
            Disposer.dispose(listenerDisposable)

            // 1) Even without any afterActionPerformed, no stale key frame is reported outside action execution, and
            //    repeated programmatic Copy calls are never latched.
            reportedAfterFailure = EditActionInputEventTracker.currentTriggeringKeyEvent()
            val provider = ComposeCopyProvider()
            provider.performCopy(context)
            provider.performCopy(context)

            // 2) Explicitly fire an unbalanced beforeActionPerformed (no afterActionPerformed) followed by a
            //    non-keyboard action — the non-keyboard action must not inherit the stale KEY_PRESSED frame.
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(AnActionListener.TOPIC)
                .beforeActionPerformed(failingAction, keyActionEvent)
            unbalancedFramesBeforeNonKey = EditActionInputEventTracker.activeFrameCountForTest()
            val nonKeyAction =
                object : AnAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT

                    override fun actionPerformed(e: AnActionEvent) {
                        reportedInsideNonKeyAction = EditActionInputEventTracker.currentTriggeringKeyEvent()
                        provider.performCopy(e.dataContext)
                        provider.performCopy(e.dataContext)
                    }
                }
            val nonKeyEvent =
                AnActionEvent.createEvent(nonKeyAction, context, null, ActionPlaces.TOOLBAR, ActionUiKind.TOOLBAR, null)
            ActionUtil.performAction(nonKeyAction, nonKeyEvent)
        }

        assertTrue(
            "unbalanced beforeActionPerformed must leave a frame before cleanup",
            unbalancedFramesBeforeNonKey > 0,
        )
        assertNull("no stale key event may be reported outside action execution", reportedAfterFailure)
        assertNull("non-keyboard action must never inherit a stale KEY_PRESSED frame", reportedInsideNonKeyAction)
        assertEquals("all four programmatic Copy invocations must execute without latching", 4, copyCount)
    }

    @Test
    fun `a stale nested frame from an unregistered action never shadows the enclosing keyboard action`() {
        editActionsEnabled = true
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        ComposeCopyProvider()

        val outerKey = keyPressed(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        val staleKey = keyPressed(KeyEvent.VK_C)
        val outerId = "test.jewel.tracker.outer"
        var observed: KeyEvent? = null
        var framesAfterOuter = -1
        runInEdtAndWait {
            val context = DataManager.getInstance().getDataContext(inner)
            val staleAction =
                object : AnAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT

                    override fun actionPerformed(e: AnActionEvent) = Unit
                }
            val outer =
                object : AnAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT

                    override fun actionPerformed(e: AnActionEvent) {
                        val staleEvent =
                            AnActionEvent.createEvent(
                                staleAction,
                                context,
                                null,
                                ActionPlaces.KEYBOARD_SHORTCUT,
                                ActionUiKind.NONE,
                                staleKey,
                            )
                        ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AnActionListener.TOPIC)
                            .beforeActionPerformed(staleAction, staleEvent)
                        observed = EditActionInputEventTracker.currentTriggeringKeyEvent()
                    }
                }
            ActionManager.getInstance().registerAction(outerId, outer)
            try {
                val outerEvent =
                    AnActionEvent.createEvent(
                        outer,
                        context,
                        null,
                        ActionPlaces.KEYBOARD_SHORTCUT,
                        ActionUiKind.NONE,
                        outerKey,
                    )
                ActionUtil.performAction(outer, outerEvent)
                framesAfterOuter = EditActionInputEventTracker.activeFrameCountForTest()
            } finally {
                ActionManager.getInstance().unregisterAction(outerId)
            }
        }
        assertSame("the enclosing keyboard action's KeyEvent must win over a stale nested frame", outerKey, observed)
        assertEquals("enclosing afterActionPerformed must trim stale nested frames", 0, framesAfterOuter)
    }

    @Test
    fun `template mnemonic survives resolved update when the focused binding inherits template text`() {
        val bridgeAction = JewelActionBridgeAction()
        bridgeAction.templatePresentation.setText("_Inner Action", true)
        val templateMnemonic = bridgeAction.templatePresentation.mnemonic
        assertEquals("template presentation must parse mnemonic from '_Inner Action'", 'I'.code, templateMnemonic)
        ActionManager.getInstance().registerAction(INNER_ID, bridgeAction)

        innerPresentationOverride = ActionPresentationOverride.Empty
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        val presentation = bridgeAction.templatePresentation.clone()
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(inner), presentation)
        assertTrue(presentation.isEnabled)
        assertEquals("Inner Action", presentation.text)
        assertEquals(
            "resolved update inheriting template text must keep template mnemonic",
            templateMnemonic,
            presentation.mnemonic,
        )

        // Explicit text override disables mnemonic parsing for literal ampersands/underscores; blur restores template.
        innerPresentationOverride = ActionPresentationOverride(text = PresentationValue.Set("Find & Replace_Literal"))
        compose.waitForIdle()
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(inner), presentation)
        assertEquals("Find & Replace_Literal", presentation.text)
        assertEquals(-1, presentation.displayedMnemonicIndex)

        val outside = JTextField()
        awtFocusOn(outside)
        updateAction(bridgeAction, DataManager.getInstance().getDataContext(outside), presentation)
        assertFalse(presentation.isEnabled)
        assertEquals(templateMnemonic, presentation.mnemonic)
    }
}

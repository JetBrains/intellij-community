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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.icons.IconManager
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.installBridgeClaimLane
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchRejection
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchResult
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionPropertyNames
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.MenuDismissPolicy
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.ShortcutRepeatPolicy
import org.jetbrains.jewel.foundation.shortcut.claimShortcut
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
 * Platform-level integration tests for manifest plugin loading, standard edit actions (`Copy`/`SelectAll`),
 * `OnceUntilRelease`, IDE chord vs claim interaction, `ActionPromoter.suppress()`, production context resolution, and
 * full toolbar presentation reset.
 */
internal class JewelBridgeActionPlatformIntegrationTest {
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

        const val KEYMAP_NAME = "JewelBridgeActionPlatformIntegrationTestKeymap"
        const val ACTION_ID = "test.jewel.platform.action"

        val STROKE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK)
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
    private lateinit var wrapper: JewelComposePanelWrapper
    private lateinit var inner: JTextField

    private val state = JewelShortcutHostState { InMemoryJewelKeymap("bridge-claims-only") }
    private val focusNode = FocusRequester()

    private val jewelAction = JewelAction(JewelActionId(ACTION_ID), "Platform Action")
    private val outerAction = JewelAction(JewelActionId("test.jewel.platform.outer"), "Outer Action")
    private val manifestAction = JewelAction(JewelActionId("test.jewel.platform.manifest"), "Manifest Action")
    private val manifestDumbAction =
        JewelAction(JewelActionId("test.jewel.platform.manifest.dumb"), "Manifest Dumb Action")

    private var count = 0
    private var outerCount = 0
    private var claimCount = 0
    private var copyCount = 0
    private var selectAllCount = 0
    private var manifestCount = 0
    private var manifestDumbCount = 0

    private var overridePresentation by mutableStateOf(ActionPresentationOverride.Empty)
    private var repeatPolicy by mutableStateOf(ShortcutRepeatPolicy.RepeatWhileHeld)
    private var claimCtrlCEnabled by mutableStateOf(false)
    private var outerActionEnabled by mutableStateOf(false)
    private var copyEnabled by mutableStateOf(false)
    private var selectAllEnabled by mutableStateOf(false)

    @Before
    fun setUp() {
        savedFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        testDisposable = Disposer.newDisposable("JewelBridgeActionPlatformIntegrationTest")
        val dataManager = DataManager.getInstance() as HeadlessDataManager
        @Suppress("DEPRECATION")
        dataManager.setTestDataProvider(
            { dataId ->
                when {
                    JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> findJewelShortcutHost(awtFocusOwner)
                    PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> awtFocusOwner
                    PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> ComposeCopyProvider()
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
            wrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            inner = JTextField()
            wrapper.composePanel.add(inner)
            wrapper.shortcutHostState = state
        }

        compose.setContent {
            Box(state.resolverRootModifier.shortcut(outerAction, enabled = outerActionEnabled) { outerCount++ }) {
                Box(
                    Modifier.shortcut(manifestAction) { manifestCount++ }
                        .shortcut(manifestDumbAction) { manifestDumbCount++ }
                        .shortcut(JewelActions.Copy, enabled = copyEnabled) { copyCount++ }
                        .shortcut(JewelActions.SelectAll, enabled = selectAllEnabled) { selectAllCount++ }
                        .shortcut(jewelAction, repeatPolicy = repeatPolicy, presentation = overridePresentation) {
                            count++
                        }
                        .claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.C, ctrl = true)),
                            enabled = claimCtrlCEnabled,
                        ) {
                            claimCount++
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

    private fun registerDeclared(): JewelActionBridgeAction {
        val action = JewelActionBridgeAction()
        ActionManager.getInstance().registerAction(ACTION_ID, action)
        keymap.addShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
        return action
    }

    @Test
    fun `plugin xml manifest loads bridge actions with default shortcuts and attaches via registry`() {
        val pluginsDir = Files.createTempDirectory("jewel-manifest-action-test")
        lateinit var pluginDisposable: Disposable
        runInEdtAndWait {
            pluginDisposable =
                loadPluginWithText(
                    plugin("org.jetbrains.jewel.test.manifest.actions") {
                        name = "Jewel Manifest Actions Test"
                        depends("com.intellij.modules.platform")
                        actions =
                            """
                            <action id="${manifestAction.id.value}"
                                    class="org.jetbrains.jewel.bridge.actionSystem.JewelActionBridgeAction"
                                    text="Manifest Bridge Action">
                              <keyboard-shortcut keymap="${'$'}default" first-keystroke="alt L"/>
                            </action>
                            <action id="${manifestDumbAction.id.value}"
                                    class="org.jetbrains.jewel.bridge.actionSystem.DumbAwareJewelActionBridgeAction"
                                    text="Manifest Dumb Bridge Action">
                              <keyboard-shortcut keymap="${'$'}default" first-keystroke="alt K"/>
                            </action>
                            """
                                .trimIndent()
                    },
                    pluginsDir,
                )
        }
        val defaultKeymap = KeymapManagerEx.getInstanceEx().getKeymap("\$default")
        assertNotNull("\$default keymap must exist", defaultKeymap)
        try {
            val actionManager = ActionManager.getInstance()
            val declared = actionManager.getAction(manifestAction.id.value)
            val declaredDumb = actionManager.getAction(manifestDumbAction.id.value)
            assertTrue("manifest must instantiate JewelActionBridgeAction", declared is JewelActionBridgeAction)
            assertTrue(
                "manifest must instantiate DumbAwareJewelActionBridgeAction",
                declaredDumb is DumbAwareJewelActionBridgeAction,
            )

            // Attach via registry (including non-empty defaultShortcuts) and verify closing never unregisters the
            // manifest-declared action.
            val registry = JewelBridgeActionRegistry(actionManager, wrapper)
            val reg =
                registry.register(
                    JewelActionDefinition(
                        manifestAction,
                        defaultShortcuts = listOf(JewelKeySequence(JewelKeyStroke(Key.L, alt = true))),
                    )
                )
            reg.close()
            assertTrue(actionManager.getAction(manifestAction.id.value) === declared)

            KeymapManagerEx.getInstanceEx().activeKeymap = defaultKeymap!!
            focusComposeNode(focusNode)
            awtFocusOn(inner)
            val altL = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.ALT_DOWN_MASK)
            val altK = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.ALT_DOWN_MASK)

            assertTrue("manifest <keyboard-shortcut> must dispatch JewelActionBridgeAction", dispatch(inner, altL))
            assertTrue(
                "manifest <keyboard-shortcut> must dispatch DumbAwareJewelActionBridgeAction",
                dispatch(inner, altK),
            )
            assertEquals(1, manifestCount)
            assertEquals(1, manifestDumbCount)
        } finally {
            KeymapManagerEx.getInstanceEx().activeKeymap = keymap
            runInEdtAndWait { Disposer.dispose(pluginDisposable) }
            pluginsDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `component-scoped ActionUtil wrap shortcut dispatches and surfaces via shortcutsFor`() {
        val registry = JewelBridgeActionRegistry(ActionManager.getInstance(), wrapper)
        val reg = registry.register(JewelActionDefinition(jewelAction))
        val altP = KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val wrapped = ActionUtil.wrap(ACTION_ID)
        wrapped.registerCustomShortcutSet(CustomShortcutSet(altP), wrapper, testDisposable)
        try {
            focusComposeNode(focusNode)
            awtFocusOn(inner)

            val expected = listOf(JewelKeySequence(JewelKeyStroke(Key(KeyEvent.VK_P), alt = true)))
            runInEdtAndWait {
                assertEquals(expected, registry.shortcutsFor(jewelAction.id))
                // Also verify componentShortcutsFor walks up Swing ancestors from a nested child component.
                val childRegistry = JewelBridgeActionRegistry(ActionManager.getInstance(), inner)
                assertEquals(expected, childRegistry.shortcutsFor(jewelAction.id))
            }
            assertTrue(dispatch(inner, altP))
            assertEquals(1, count)
        } finally {
            wrapped.unregisterCustomShortcutSet(wrapper)
            reg.close()
        }
    }

    @Test
    fun `the IDE Copy key runs the focused JewelActions Copy handler`() {
        JewelActionMappings.installStandardMappings()
        copyEnabled = true
        compose.waitForIdle()
        val copyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_COPY, KeyboardShortcut(copyStroke, null))
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        dispatch(inner, copyStroke)

        assertEquals("Ctrl+C did not reach the Modifier.shortcut(JewelActions.Copy) handler", 1, copyCount)
    }

    @Test
    fun `JewelActions SelectAll works via active keymap, invoker, and sampledPresentation when focused`() {
        JewelActionMappings.installStandardMappings()
        val selectAllStroke = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_SELECT_ALL, KeyboardShortcut(selectAllStroke, null))
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        val removeLane = installBridgeClaimLane(wrapper, state)
        val registry = JewelBridgeActionRegistry(ActionManager.getInstance(), wrapper)
        try {
            // Disabled while SelectAll binding is off: plain Compose TextField keeps Ctrl+A.
            selectAllEnabled = false
            compose.waitForIdle()
            assertFalse("unfocused/disabled SelectAll must not consume", dispatch(inner, selectAllStroke))
            runInEdtAndWait {
                val initialDisabled = registry.sampledPresentation(JewelActions.SelectAll.id)
                assertFalse(
                    "initial SelectAll sample must be disabled without a focused binding",
                    initialDisabled!!.enabled,
                )
            }
            registry.awaitPendingSamplesForTest()
            assertFalse(
                "settled SelectAll sample must be disabled without a focused binding",
                registry.withCachedOnlyRead { registry.sampledPresentation(JewelActions.SelectAll.id) }!!.enabled,
            )

            // Enabled: keyboard dispatch, invoker, and sampledPresentation all agree.
            selectAllEnabled = true
            compose.waitForIdle()
            assertTrue("SelectAll shortcut must dispatch to JewelActions.SelectAll", dispatch(inner, selectAllStroke))
            assertEquals(1, selectAllCount)

            var invokerResult: ActionDispatchResult? = null
            // Await async sampling before tryToExecute (which cancels in-flight ActionUpdater sessions).
            runInEdtAndWait { registry.sampledPresentation(JewelActions.SelectAll.id) }
            registry.awaitPendingSamplesForTest()
            assertTrue(
                "SelectAll sample must be enabled when a focused binding exists",
                registry.withCachedOnlyRead { registry.sampledPresentation(JewelActions.SelectAll.id) }!!.enabled,
            )
            runInEdtAndWait {
                invokerResult = JewelBridgeActionInvoker(wrapper).invoke(JewelActions.SelectAll, ActionTrigger.Pointer)
                UIUtil.dispatchAllInvocationEvents()
            }
            assertEquals(ActionDispatchResult.Dispatched, invokerResult)
            assertEquals(2, selectAllCount)
        } finally {
            registry.cancelPendingSamples()
            removeLane()
        }
    }

    @Test
    fun `a held key runs a OnceUntilRelease command once and re-arms on KEY_RELEASED`() {
        repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease
        compose.waitForIdle()
        registerDeclared()
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        val removeLane = installBridgeClaimLane(wrapper, state)
        try {
            // Auto-repeat: two KEY_PRESSED events without a KEY_RELEASED in between.
            dispatch(inner, STROKE)
            dispatch(inner, STROKE)
            assertEquals("held key must run OnceUntilRelease command once", 1, count)

            runInEdtAndWait {
                val release =
                    KeyEvent(
                        inner,
                        KeyEvent.KEY_RELEASED,
                        System.currentTimeMillis(),
                        STROKE.modifiers,
                        STROKE.keyCode,
                        KeyEvent.CHAR_UNDEFINED,
                    )
                KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(release)
            }
            dispatch(inner, STROKE)
            assertEquals("OnceUntilRelease command must re-arm after KEY_RELEASED", 2, count)
        } finally {
            removeLane()
        }
    }

    @Test
    fun `the second stroke of an IDE chord does not also run a Jewel claim`() {
        claimCtrlCEnabled = true
        compose.waitForIdle()
        var chordRuns = 0
        val chordActionId = "test.jewel.platform.chord"
        val chordAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun actionPerformed(e: AnActionEvent) {
                    chordRuns++
                }
            }
        val ctrlK = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
        val ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
        ActionManager.getInstance().registerAction(chordActionId, chordAction)
        keymap.addShortcut(chordActionId, KeyboardShortcut(ctrlK, ctrlC))
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        val removeLane = installBridgeClaimLane(wrapper, state)
        try {
            val ideDispatcher = IdeKeyEventDispatcher(null)
            runInEdtAndWait {
                for (stroke in listOf(ctrlK, ctrlC)) {
                    val event =
                        KeyEvent(
                            inner,
                            KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(),
                            stroke.modifiers,
                            stroke.keyCode,
                            KeyEvent.CHAR_UNDEFINED,
                        )
                    if (ideDispatcher.dispatchKeyEvent(event)) event.consume()
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(event)
                }
            }

            assertEquals("the IDE chord did not run", 1, chordRuns)
            assertEquals("the chord's second stroke also ran the Jewel Ctrl+C claim", 0, claimCount)
        } finally {
            removeLane()
            ActionManager.getInstance().unregisterAction(chordActionId)
        }
    }

    @Test
    fun `two IDE Jewel actions on one key run the innermost focused binding without displacing non-Jewel actions`() {
        outerActionEnabled = true
        compose.waitForIdle()
        val actionManager = ActionManager.getInstance()
        val outerBridge = JewelActionBridgeAction()
        val innerBridge = JewelActionBridgeAction()
        actionManager.registerAction(outerAction.id.value, outerBridge)
        actionManager.registerAction(ACTION_ID, innerBridge)
        try {
            keymap.addShortcut(outerAction.id.value, KeyboardShortcut(STROKE, null))
            keymap.addShortcut(ACTION_ID, KeyboardShortcut(STROKE, null))
            focusComposeNode(focusNode)
            awtFocusOn(inner)

            assertTrue(dispatch(inner, STROKE))
            assertEquals("outer binding must be suppressed by ActionPromoter.suppress", 0, outerCount)
            assertEquals("inner binding must run", 1, count)

            // Verify suppress() never moves Jewel actions ahead of a leading non-Jewel action (e.g. SpeedSearch).
            val platformAction =
                object : AnAction() {
                    override fun actionPerformed(e: AnActionEvent) = Unit
                }
            val dataContext = DataManager.getInstance().getDataContext(inner)
            val candidates = listOf<AnAction>(platformAction, outerBridge, innerBridge)
            val suppressed = outerBridge.suppress(candidates, dataContext)
            assertEquals(listOf<AnAction>(outerBridge), suppressed)
            assertNull("JewelActionBridgeAction must not use promote()", outerBridge.promote(candidates, dataContext))
        } finally {
            actionManager.unregisterAction(outerAction.id.value)
        }
    }

    @Test
    fun `invoker and sampledPresentation pass focused child inside ComposePanel and propagate Disabled rejection`() {
        JewelActionMappings.installStandardMappings()
        var invokerContextComponent: Component? = null
        var sampledContextComponent: Component? = null
        val recordingCopyProvider =
            object : CopyProvider {
                override fun performCopy(dataContext: DataContext) = Unit

                override fun isCopyEnabled(dataContext: DataContext): Boolean {
                    val comp = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
                    if (invokerContextComponent == null) {
                        invokerContextComponent = comp
                    } else {
                        sampledContextComponent = comp
                    }
                    return false
                }

                override fun isCopyVisible(dataContext: DataContext): Boolean = true

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            }
        val prodDisposable = Disposer.newDisposable(testDisposable, "production data context")
        @Suppress("DEPRECATION")
        (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider({ null }, prodDisposable)
        HeadlessDataManager.fallbackToProductionDataManager(prodDisposable)
        wrapper.targetProvider = UiDataProvider { sink -> sink[PlatformDataKeys.COPY_PROVIDER] = recordingCopyProvider }
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        var result: ActionDispatchResult? = null
        val registry = JewelBridgeActionRegistry(ActionManager.getInstance(), wrapper)
        try {
            runInEdtAndWait {
                JPanel().add(wrapper)
                result = JewelBridgeActionInvoker(wrapper).invoke(JewelActions.Copy)
                registry.sampledPresentation(JewelActions.Copy.id)
            }
            registry.awaitPendingSamplesForTest()
            val sampledEnabled =
                registry.withCachedOnlyRead { registry.sampledPresentation(JewelActions.Copy.id)?.enabled }

            assertEquals(
                "invoker must pass focused descendant inside ComposePanel",
                wrapper.composePanel,
                invokerContextComponent?.parent,
            )
            assertEquals(
                "sampledPresentation must pass focused descendant inside ComposePanel",
                wrapper.composePanel,
                sampledContextComponent?.parent,
            )
            assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.Disabled), result)
            assertEquals(false, sampledEnabled)
        } finally {
            registry.cancelPendingSamples()
        }
    }

    @Test
    fun `a toolbar entry resets override text, description, icon, keepPopupOnPerform, and client properties on blur`() {
        val bridgeAction = registerDeclared()
        bridgeAction.templatePresentation.text = "Template text"
        bridgeAction.templatePresentation.description = "Template description"
        bridgeAction.templatePresentation.icon = AllIcons.Actions.MenuSaveall
        bridgeAction.templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
        overridePresentation =
            ActionPresentationOverride(
                text = PresentationValue.Set("Override text"),
                description = PresentationValue.Set("Override description"),
                selected = PresentationValue.Set(true),
                menuDismissPolicy = PresentationValue.Set(MenuDismissPolicy.KeepAlways),
                properties = mapOf(ActionPropertyNames.ShowTextInToolbar to true),
            )
        compose.waitForIdle()
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        val toolbar = DefaultActionGroup(bridgeAction)
        val presentations = PresentationFactory()

        fun updateToolbar(focusOwner: Component) = runInEdtAndWait {
            Utils.expandActionGroup(
                toolbar,
                presentations,
                DataManager.getInstance().getDataContext(focusOwner),
                ActionPlaces.TOOLBAR,
                ActionUiKind.TOOLBAR,
            )
        }

        updateToolbar(inner)
        val activePres = presentations.getPresentation(bridgeAction)
        assertTrue(activePres.isEnabled)
        assertEquals("Override text", activePres.text)
        assertEquals("Override description", activePres.description)
        assertTrue(Toggleable.isSelected(activePres))
        assertEquals(true, activePres.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))
        assertEquals(KeepPopupOnPerform.Always, activePres.keepPopupOnPerform)

        val outside = JTextField()
        awtFocusOn(outside)
        updateToolbar(outside)

        val resetPres = presentations.getPresentation(bridgeAction)
        assertFalse("the entry is still enabled", resetPres.isEnabled)
        assertEquals("Template text", resetPres.text)
        assertEquals("Template description", resetPres.description)
        assertEquals(AllIcons.Actions.MenuSaveall, resetPres.icon)
        assertFalse("Toggleable.SELECTED_KEY must reset when focus leaves", Toggleable.isSelected(resetPres))
        assertNull(
            "SHOW_TEXT_IN_TOOLBAR must reset to template value",
            resetPres.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR),
        )
        assertEquals(KeepPopupOnPerform.IfRequested, resetPres.keepPopupOnPerform)
    }
}

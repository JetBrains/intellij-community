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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
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
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.installBridgeClaimLane
import org.jetbrains.jewel.bridge.menuShortcut.BridgeMenuItemShortcutProvider
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchRejection
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchResult
import org.jetbrains.jewel.foundation.shortcut.ActionPresentation
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionTemplatePresentation
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.MenuDismissPolicy
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.ShortcutRepeatPolicy
import org.jetbrains.jewel.foundation.shortcut.claimShortcut
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Platform integration tests covering focusable popup context routing, `sampledPresentation` EDT/BGT `UpdateSession`
 * and lock-prohibited async updates, `KeepPopupOnPerform` and `Presentation.getIconDescriptor`, `shortcutsFor`
 * local+global collection, explicit `PresentationValue.Set` vs `Inherit`, BGT-safe equal-depth promoter suppression,
 * nested `beforeActionPerformed` tracking, `IdeEventQueue` `KEY_RELEASED` preprocessing, and
 * `ActionDispatchRejection.RejectedByHost`.
 */
internal class JewelBridgeActionDispatchAndSamplingIntegrationTest {
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

        const val KEYMAP_NAME = "JewelBridgeActionDispatchAndSamplingKeymap"
        const val INNER_ID = "test.jewel.round6.inner"
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
    private val innerAction = JewelAction(JewelActionId(INNER_ID), "Inner Action")

    private var innerPresentationOverride by mutableStateOf(ActionPresentationOverride.Empty)
    private var claimCount = 0

    @Before
    fun setUp() {
        savedFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        JewelActionMappings.installStandardMappings()
        testDisposable = Disposer.newDisposable("JewelBridgeActionDispatchAndSamplingIntegrationTest")
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
            parentPanel = JPanel()
            wrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            inner = JTextField()
            wrapper.composePanel.add(inner)
            parentPanel.add(wrapper)
            wrapper.shortcutHostState = state
        }

        compose.setContent {
            Box(state.resolverRootModifier) {
                Box(
                    Modifier.testTag("focusNode")
                        .shortcut(innerAction, presentation = innerPresentationOverride) {}
                        .claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.L, ctrl = true)),
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            claimCount++
                        }
                        .semantics { copyText { true } }
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
        if (actionManager.getAction(INNER_ID) != null) {
            actionManager.unregisterAction(INNER_ID)
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

    private fun keyPressed(source: Component, keyCode: Int, modifiers: Int = InputEvent.CTRL_DOWN_MASK): KeyEvent =
        KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)

    @Test
    fun `focusable popup wrapper routes resolveContextComponent, invoker, and edit providers to popup until closed`() {
        lateinit var popupWrapper: JewelComposePanelWrapper
        runInEdtAndWait {
            popupWrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            popupWrapper.shortcutHostState = state
        }
        awtFocusOn(inner)

        // With no active popup, context resolves to the focused descendant inside the owner wrapper.
        assertSame(inner, resolveContextComponent(wrapper))

        // While a focusable popup is active (AWT focus remains on owner), context routes to popup.composePanel.
        wrapper.pushActivePopupWrapper(popupWrapper)
        try {
            assertSame(popupWrapper.composePanel, resolveContextComponent(wrapper))

            var seenPanelByCopyProvider: Component? = null
            var observedByInvoker: Component? = null
            val probeId = "test.jewel.round6.popupProbe"
            val probeAction =
                object : AnAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = true
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        observedByInvoker = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
                    }
                }
            val prodDisposable = Disposer.newDisposable(testDisposable, "popup production context")
            @Suppress("DEPRECATION")
            (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider({ null }, prodDisposable)
            HeadlessDataManager.fallbackToProductionDataManager(prodDisposable)
            val actionManager = ActionManager.getInstance()
            actionManager.registerAction(probeId, probeAction)
            try {
                runInEdtAndWait {
                    val invokerResult =
                        JewelBridgeActionInvoker(wrapper).invoke(JewelAction(JewelActionId(probeId), "Probe"))
                    assertEquals(ActionDispatchResult.Dispatched, invokerResult)
                    UIUtil.dispatchAllInvocationEvents()

                    val popupDataContext = DataManager.getInstance().getDataContext(resolveContextComponent(wrapper))
                    val focusSemanticsNode = compose.onNodeWithTag("focusNode").fetchSemanticsNode()
                    val copyProvider = ComposeCopyProvider { panel ->
                        seenPanelByCopyProvider = panel
                        focusSemanticsNode
                    }
                    assertTrue(copyProvider.isCopyEnabled(popupDataContext))
                    copyProvider.performCopy(popupDataContext)
                }
                assertSame(
                    "invoker must target active popup ComposePanel",
                    popupWrapper.composePanel,
                    observedByInvoker,
                )
                assertSame(
                    "ComposeCopyProvider must target active popup ComposePanel",
                    popupWrapper.composePanel,
                    seenPanelByCopyProvider,
                )
            } finally {
                actionManager.unregisterAction(probeId)
                Disposer.dispose(prodDisposable)
            }
        } finally {
            wrapper.popActivePopupWrapper(popupWrapper)
        }
        assertSame("closing popup must restore owner focus routing", inner, resolveContextComponent(wrapper))
    }

    @Test
    fun `sampledPresentation runs EDT UpdateSession, BGT actions, KeepPopupOnPerform, and async lock-prohibited updates`() {
        val edtId = "test.jewel.round6.edtSession"
        val bgtId = "test.jewel.round6.bgtSample"
        val actionManager = ActionManager.getInstance()

        var edtSessionComputed = false
        val edtAction =
            object : AnAction("EDT Template") {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    val computed = e.updateSession.compute(this, "probe", ActionUpdateThread.EDT) { "EDT Computed" }
                    edtSessionComputed = computed == "EDT Computed"
                    e.presentation.text = computed
                    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.IfPreferred
                    e.presentation.isEnabled = true
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }

        var bgtRanOffEdt = false
        var bgtText = "BGT Sync"
        val bgtAction =
            object : AnAction("BGT Template") {
                override fun getActionUpdateThread() = ActionUpdateThread.BGT

                override fun update(e: AnActionEvent) {
                    bgtRanOffEdt = !EDT.isCurrentThreadEdt()
                    e.presentation.text = bgtText
                    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
                    e.presentation.isEnabled = true
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }

        actionManager.registerAction(edtId, edtAction)
        actionManager.registerAction(bgtId, bgtAction)
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        try {
            var edtInitial: ActionPresentation? = null
            var bgtInitial: ActionPresentation? = null
            runInEdtAndWait {
                edtInitial = registry.sampledPresentation(JewelActionId(edtId))
                bgtInitial = registry.sampledPresentation(JewelActionId(bgtId))
            }
            assertFalse("initial pending EDT sample must stay disabled", edtInitial!!.enabled)
            assertFalse("initial pending BGT sample must stay disabled", bgtInitial!!.enabled)

            registry.awaitPendingSamplesForTest()
            val edtSample = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(edtId)) }
            val bgtSample = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(bgtId)) }

            assertTrue("EDT action must receive an initialized UpdateSession", edtSessionComputed)
            assertEquals("EDT Computed", edtSample?.text)
            assertEquals(MenuDismissPolicy.KeepIfPreferred, edtSample?.menuDismissPolicy)

            assertTrue("BGT action update() must execute off EDT", bgtRanOffEdt)
            assertEquals("BGT Sync", bgtSample?.text)
            assertEquals(MenuDismissPolicy.KeepAlways, bgtSample?.menuDismissPolicy)

            // Under lock-prohibited EDT advice, sampling must not block/throw: it returns the cached sample and
            // fires onAsyncSampleUpdated when the deferred expansion finishes.
            bgtText = "BGT Async Updated"
            val updatedLatch = CountDownLatch(1)
            var notifiedId: JewelActionId? = null
            registry.onAsyncSampleUpdated = { id ->
                notifiedId = id
                updatedLatch.countDown()
            }
            var immediateSample: ActionPresentation? = null
            runInEdtAndWait {
                val app = ApplicationManager.getApplication()
                val withLocksProhibited =
                    app.javaClass.getMethod("withLocksProhibited", String::class.java, Supplier::class.java)
                immediateSample =
                    withLocksProhibited.invoke(
                        app,
                        "round6 test",
                        Supplier { registry.sampledPresentation(JewelActionId(bgtId)) },
                    ) as ActionPresentation?
            }
            assertEquals(
                "lock-prohibited call must return last cached sample immediately",
                "BGT Sync",
                immediateSample?.text,
            )
            registry.awaitPendingSamplesForTest()
            assertTrue(
                "onAsyncSampleUpdated must fire after deferred BGT expansion",
                updatedLatch.await(1, TimeUnit.SECONDS),
            )
            assertEquals(JewelActionId(bgtId), notifiedId)
            // Verify off-EDT read now sees the asynchronously published sample without running update().
            assertEquals("BGT Async Updated", registry.sampledPresentation(JewelActionId(bgtId))?.text)
        } finally {
            registry.cancelPendingSamples()
            actionManager.unregisterAction(edtId)
            actionManager.unregisterAction(bgtId)
        }
    }

    @Test
    fun `sampledPresentation and adoptDeclaredAction prefer Presentation getIconDescriptor when experimental icons are enabled`() {
        val setIconDescriptor =
            runCatching { Presentation::class.java.getMethod("setIconDescriptor", IconDescriptor::class.java) }
                .getOrNull()
        assumeTrue("Presentation.setIconDescriptor is not present in this SDK", setIconDescriptor != null)
        val iconDescriptorSetter = checkNotNull(setIconDescriptor)

        val descriptorOnly = AllIcons.Actions.MenuSaveall.toIconDescriptor()
        val actionId = "test.jewel.round6.iconDescriptor"
        val action =
            object : AnAction("Descriptor Action") {
                init {
                    iconDescriptorSetter.invoke(templatePresentation, descriptorOnly)
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    iconDescriptorSetter.invoke(e.presentation, descriptorOnly)
                    e.presentation.isEnabled = true
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }

        val registryValue = runCatching { Registry.get("ide.experimental.icons") }.getOrNull()
        assumeTrue("ide.experimental.icons registry key unavailable", registryValue != null)
        checkNotNull(registryValue).setValue(true, testDisposable)

        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, action)
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        try {
            val adopted = registry.definition(JewelActionId(actionId))
            assertEquals(descriptorOnly, adopted?.action?.template?.icon)

            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            registry.awaitPendingSamplesForTest()
            val sampled = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertEquals(descriptorOnly, sampled?.icon)
        } finally {
            registry.cancelPendingSamples()
            actionManager.unregisterAction(actionId)
        }
    }

    @Test
    fun `runtime registration converts template IconDescriptor into Swing templatePresentation icon`() {
        val iconId = JewelActionId("test.jewel.round6.runtimeIcon")
        val descriptor = AllIcons.Actions.MenuSaveall.toIconDescriptor()
        val definition =
            JewelActionDefinition(
                JewelAction(iconId, ActionTemplatePresentation(text = "With Icon", icon = descriptor))
            )
        val actionManager = ActionManager.getInstance()
        val reg = JewelBridgeActionRegistry(actionManager, wrapper).register(definition)
        try {
            val registered = actionManager.getAction(iconId.value)
            assertNotNull("runtime action must be registered", registered)
            assertNotNull(
                "templatePresentation.icon must be populated from IconDescriptor",
                registered!!.templatePresentation.icon,
            )
        } finally {
            reg.close()
        }
    }

    @Test
    fun `shortcutsFor collects local focused-descendant ancestry and global keymap shortcuts without mouse shortcuts hiding keyboard ones`() {
        val actionManager = ActionManager.getInstance()
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        val reg = registry.register(JewelActionDefinition(innerAction))

        val childStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.ALT_DOWN_MASK)
        val parentStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.ALT_DOWN_MASK)
        val globalStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F9, InputEvent.ALT_DOWN_MASK)
        val childWrapped = ActionUtil.wrap(INNER_ID)
        val parentWrapped = ActionUtil.wrap(INNER_ID)
        val shadowAction =
            object : AnAction("Shadow Action") {
                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val mouseShortcut = MouseShortcut(MouseEvent.BUTTON2, 0, 1)

        try {
            awtFocusOn(inner)
            keymap.addShortcut(INNER_ID, mouseShortcut)
            childWrapped.registerCustomShortcutSet(CustomShortcutSet(childStroke), inner, testDisposable)
            parentWrapped.registerCustomShortcutSet(CustomShortcutSet(parentStroke), parentPanel, testDisposable)

            val expectedChild = JewelKeySequence(JewelKeyStroke(Key(KeyEvent.VK_F7), alt = true))
            val expectedParent = JewelKeySequence(JewelKeyStroke(Key(KeyEvent.VK_F8), alt = true))
            val expectedGlobal = JewelKeySequence(JewelKeyStroke(Key(KeyEvent.VK_F9), alt = true))

            runInEdtAndWait {
                // Global mouse shortcut must NOT hide local keyboard shortcuts; local ancestry is innermost-first.
                assertEquals(listOf(expectedChild, expectedParent), registry.shortcutsFor(innerAction.id))

                // Adding a global keyboard shortcut appends it after local component shortcuts.
                keymap.addShortcut(INNER_ID, KeyboardShortcut(globalStroke, null))
                assertEquals(
                    listOf(expectedChild, expectedParent, expectedGlobal),
                    registry.shortcutsFor(innerAction.id),
                )

                // A child action claiming parentStroke must shadow the ancestor shortcut for INNER_ID.
                shadowAction.registerCustomShortcutSet(CustomShortcutSet(parentStroke), inner, testDisposable)
                assertEquals(listOf(expectedChild, expectedGlobal), registry.shortcutsFor(innerAction.id))
            }

            // Off-EDT shortcutsFor must never walk Swing parent chains or throw; it returns global shortcuts only.
            var trapArmed = false
            var parentWalked = false
            val trapComponent =
                object : JPanel() {
                    override fun getParent(): Container? {
                        if (trapArmed) {
                            parentWalked = true
                            error("shortcutsFor must never walk Swing component.parent off EDT")
                        }
                        return super.getParent()
                    }
                }
            trapArmed = true
            val offEdtRegistry = JewelBridgeActionRegistry(actionManager, trapComponent)
            var offEdtShortcuts: List<JewelKeySequence>? = null
            var offEdtError: Throwable? = null
            val thread = Thread {
                try {
                    offEdtShortcuts = offEdtRegistry.shortcutsFor(innerAction.id)
                } catch (t: Throwable) {
                    offEdtError = t
                }
            }
            thread.start()
            thread.join(10_000)
            assertNull("off-EDT shortcutsFor must not throw: $offEdtError", offEdtError)
            assertFalse("off-EDT shortcutsFor must never walk Swing parent", parentWalked)
            assertEquals(listOf(expectedGlobal), offEdtShortcuts)
        } finally {
            shadowAction.unregisterCustomShortcutSet(inner)
            childWrapped.unregisterCustomShortcutSet(inner)
            parentWrapped.unregisterCustomShortcutSet(parentPanel)
            reg.close()
        }
    }

    @Test
    fun `JewelActionBridgeAction applies explicit PresentationValue Set overrides even when matching template text or clearing nullable fields`() {
        val bridgeAction = JewelActionBridgeAction()
        bridgeAction.templatePresentation.setText("_Inner Action", true)
        bridgeAction.templatePresentation.description = "Template Description"
        bridgeAction.templatePresentation.icon = AllIcons.Actions.MenuSaveall
        bridgeAction.addTextOverride(ActionPlaces.TOOLBAR, "Toolbar Override")
        ActionManager.getInstance().registerAction(INNER_ID, bridgeAction)

        focusComposeNode(focusNode)
        awtFocusOn(inner)

        // 1) Inherit keeps template mnemonic, toolbar <override-text>, description, and icon.
        innerPresentationOverride = ActionPresentationOverride.Empty
        compose.waitForIdle()
        val inherited = bridgeAction.templatePresentation.clone()
        runInEdtAndWait {
            val event =
                AnActionEvent.createEvent(
                    bridgeAction,
                    DataManager.getInstance().getDataContext(inner),
                    inherited,
                    ActionPlaces.TOOLBAR,
                    ActionUiKind.TOOLBAR,
                    null,
                )
            bridgeAction.update(event)
        }
        assertEquals("Toolbar Override", inherited.text)
        assertEquals("Template Description", inherited.description)
        assertSame(AllIcons.Actions.MenuSaveall, inherited.icon)

        // 2) Explicit Set("Inner Action") (same plain text as template!) clears mnemonic and overrides <override-text>,
        //    and explicit Set(null) clears template description and icon.
        innerPresentationOverride =
            ActionPresentationOverride(
                text = PresentationValue.Set("Inner Action"),
                description = PresentationValue.Set(null),
                icon = PresentationValue.Set(null),
            )
        compose.waitForIdle()
        val overridden = bridgeAction.templatePresentation.clone()
        runInEdtAndWait {
            val event =
                AnActionEvent.createEvent(
                    bridgeAction,
                    DataManager.getInstance().getDataContext(inner),
                    overridden,
                    ActionPlaces.TOOLBAR,
                    ActionUiKind.TOOLBAR,
                    null,
                )
            bridgeAction.update(event)
        }
        assertEquals("Inner Action", overridden.text)
        assertEquals(
            "explicit Set(templatePlainText) must clear template mnemonic",
            -1,
            overridden.displayedMnemonicIndex,
        )
        assertNull("explicit Set(null) must clear template description", overridden.description)
        assertNull("explicit Set(null) must clear template icon", overridden.icon)
    }

    @Test
    fun `suppressCompetingJewelActions is BGT-safe without walking Swing parents and keeps equal-depth actions unsuppressed`() {
        val bridgeAction = JewelActionBridgeAction()
        ActionManager.getInstance().registerAction(INNER_ID, bridgeAction)
        val wrapped = ActionUtil.wrap(INNER_ID)
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        // 1) Off-EDT without JEWEL_SHORTCUT_HOST_STATE must return null without calling component.parent.
        var trapArmed = false
        var parentAccessed = false
        val trapComponent =
            object : JPanel() {
                override fun getParent(): Container? {
                    if (trapArmed) {
                        parentAccessed = true
                        error("getParent must never be called off-EDT from ActionPromoter")
                    }
                    return super.getParent()
                }
            }
        trapArmed = true
        val bareContext = DataContext { dataId ->
            if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId)) trapComponent else null
        }
        var bgtSuppressed: List<AnAction>? = emptyList()
        var bgtError: Throwable? = null
        val thread = Thread {
            try {
                bgtSuppressed = suppressCompetingJewelActions(listOf(bridgeAction, wrapped), bareContext)
            } catch (t: Throwable) {
                bgtError = t
            }
        }
        thread.start()
        thread.join(10_000)
        assertNull("off-EDT suppressCompetingJewelActions must not throw: $bgtError", bgtError)
        assertFalse("off-EDT promoter must never read Swing component.parent", parentAccessed)
        assertNull(bgtSuppressed)

        // 2) Equal-depth candidates (global action + ActionUtil.wrap for the same action ID) stay unsuppressed.
        val hostContext = DataContext { dataId -> if (JEWEL_SHORTCUT_HOST_STATE.`is`(dataId)) state else null }
        assertNull(
            "equal-depth wrapper and global action for same ID must both stay unsuppressed",
            suppressCompetingJewelActions(listOf(wrapped, bridgeAction), hostContext),
        )
    }

    @Test
    fun `nested balanced action inside another AnActionListener beforeActionPerformed preserves enclosing keyboard KeyEvent`() {
        ComposeCopyProvider()
        val outerId = "test.jewel.round6.outerBalanced"
        val nestedId = "test.jewel.round6.nestedBalanced"
        val outerKey = keyPressed(inner, KeyEvent.VK_C)
        var observedOuterKey: KeyEvent? = null

        val nestedAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val outerAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun actionPerformed(e: AnActionEvent) {
                    observedOuterKey = EditActionInputEventTracker.currentTriggeringKeyEvent()
                }
            }

        val listenerDisposable = Disposer.newDisposable(testDisposable, "nested balanced listener")
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(outerId, outerAction)
        actionManager.registerAction(nestedId, nestedAction)
        try {
            ApplicationManager.getApplication()
                .messageBus
                .connect(listenerDisposable)
                .subscribe(
                    AnActionListener.TOPIC,
                    object : AnActionListener {
                        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                            if (action === outerAction) {
                                // Direct ComposeCopyProvider.performCopy inside beforeActionPerformed (no nested
                                // ActionThreadContext) must NOT wipe the enclosing keyboard frame.
                                ComposeCopyProvider().performCopy(event.dataContext)
                                val nestedEvent =
                                    AnActionEvent.createEvent(
                                        nestedAction,
                                        event.dataContext,
                                        null,
                                        ActionPlaces.TOOLBAR,
                                        ActionUiKind.TOOLBAR,
                                        null,
                                    )
                                ActionUtil.performAction(nestedAction, nestedEvent)
                            }
                        }
                    },
                )
            runInEdtAndWait {
                val context = DataManager.getInstance().getDataContext(inner)
                val outerEvent =
                    AnActionEvent.createEvent(
                        outerAction,
                        context,
                        null,
                        ActionPlaces.KEYBOARD_SHORTCUT,
                        ActionUiKind.NONE,
                        outerKey,
                    )
                ActionUtil.performAction(outerAction, outerEvent)
            }
            assertSame(
                "balanced nested action inside beforeActionPerformed must not erase outer keyboard frame",
                outerKey,
                observedOuterKey,
            )
        } finally {
            Disposer.dispose(listenerDisposable)
            actionManager.unregisterAction(outerId)
            actionManager.unregisterAction(nestedId)
        }
    }

    @Test
    fun `IdeEventQueue KEY_RELEASED preprocessor clears OnceUntilRelease latches even when intercepted before KeyboardFocusManager`() {
        focusComposeNode(focusNode)
        awtFocusOn(inner)
        val removeLane = installBridgeClaimLane(wrapper, state)
        val popupInterceptorDisposable = Disposer.newDisposable(testDisposable, "popup release interceptor")
        var dispatcherInterceptedRelease = false
        var focusManagerSawRelease = false
        val sentinelDispatcher =
            java.awt.KeyEventDispatcher { event ->
                if (event.id == KeyEvent.KEY_RELEASED && event.keyCode == KeyEvent.VK_L) {
                    focusManagerSawRelease = true
                }
                false
            }
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addKeyEventDispatcher(sentinelDispatcher)
        try {
            // Simulate a real IdeEventQueue dispatcher consuming KEY_RELEASED before KeyboardFocusManager runs.
            IdeEventQueue.getInstance()
                .addDispatcher(
                    object : IdeEventQueue.NonLockedEventDispatcher {
                        override fun dispatch(e: java.awt.AWTEvent): Boolean {
                            val isRelease = e is KeyEvent && e.id == KeyEvent.KEY_RELEASED
                            if (isRelease) dispatcherInterceptedRelease = true
                            return isRelease
                        }
                    },
                    popupInterceptorDisposable,
                )
            runInEdtAndWait {
                val fm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                fm.dispatchEvent(keyPressed(inner, KeyEvent.VK_L))
                fm.dispatchEvent(keyPressed(inner, KeyEvent.VK_L))
                assertEquals("held repeat must be suppressed under OnceUntilRelease", 1, claimCount)

                val release =
                    KeyEvent(
                        inner,
                        KeyEvent.KEY_RELEASED,
                        System.currentTimeMillis(),
                        InputEvent.CTRL_DOWN_MASK,
                        KeyEvent.VK_L,
                        KeyEvent.CHAR_UNDEFINED,
                    )
                IdeEventQueue.getInstance().dispatchEvent(release)

                assertTrue("NonLockedEventDispatcher must intercept KEY_RELEASED", dispatcherInterceptedRelease)
                assertFalse("KeyboardFocusManager must never see intercepted KEY_RELEASED", focusManagerSawRelease)

                fm.dispatchEvent(keyPressed(inner, KeyEvent.VK_L))
                assertEquals("IdeEventQueue KEY_RELEASED preprocessor must re-arm OnceUntilRelease", 2, claimCount)
            }
        } finally {
            focusManager.removeKeyEventDispatcher(sentinelDispatcher)
            Disposer.dispose(popupInterceptorDisposable)
            removeLane()
        }
    }

    @Test
    fun `JewelBridgeActionInvoker distinguishes update-time Disabled from execution-time and write-lock RejectedByHost`() {
        val disabledId = "test.jewel.round6.disabled"
        val skippedId = "test.jewel.round6.skipped"
        val enabledId = "test.jewel.round7.writeLockEnabled"
        val disabledAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val skippedAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = true
                    e.presentation.putClientProperty(ActionUtil.SKIP_ACTION_EXECUTION, true)
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val enabledAction =
            object : AnAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = true
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }

        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(disabledId, disabledAction)
        actionManager.registerAction(skippedId, skippedAction)
        actionManager.registerAction(enabledId, enabledAction)
        try {
            awtFocusOn(inner)
            var disabledResult: ActionDispatchResult? = null
            var skippedResult: ActionDispatchResult? = null
            var writeLockedResult: ActionDispatchResult? = null
            runInEdtAndWait {
                val invoker = JewelBridgeActionInvoker(wrapper)
                disabledResult = invoker.invoke(JewelAction(JewelActionId(disabledId), "Disabled"))
                skippedResult = invoker.invoke(JewelAction(JewelActionId(skippedId), "Skipped"))
                ApplicationManager.getApplication().runWriteAction {
                    writeLockedResult = invoker.invoke(JewelAction(JewelActionId(enabledId), "Enabled Under Write"))
                }
            }
            assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.Disabled), disabledResult)
            assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.RejectedByHost), skippedResult)
            assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.RejectedByHost), writeLockedResult)
        } finally {
            actionManager.unregisterAction(disabledId)
            actionManager.unregisterAction(skippedId)
            actionManager.unregisterAction(enabledId)
        }
    }

    @Test
    fun `BridgeMenuItemShortcutProvider skips active keymap chords on Cut and picks single-stroke shortcuts`() {
        val firstStroke = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
        val secondStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
        val singleStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)

        keymap.addShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(firstStroke, secondStroke))
        assertNull(
            "2-stroke chord on \$Cut must return null rather than truncating to first stroke",
            BridgeMenuItemShortcutProvider.getShortcutKeyStroke(ContextMenuItemOptionAction.CutMenuItemOptionAction),
        )

        keymap.addShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(singleStroke, null))
        assertEquals(
            "single-stroke shortcut on \$Cut must be chosen when paired with a 2-stroke chord",
            singleStroke,
            BridgeMenuItemShortcutProvider.getShortcutKeyStroke(ContextMenuItemOptionAction.CutMenuItemOptionAction),
        )
    }
}

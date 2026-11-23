// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.cutText
import androidx.compose.ui.semantics.pasteText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.component.ActivePopupWrapperEffect
import org.jetbrains.jewel.bridge.installBridgeClaimLane
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.shortcut.ActionPresentationOverride
import org.jetbrains.jewel.foundation.shortcut.ActionTemplatePresentation
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.PresentationValue
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.jetbrains.jewel.ui.LocalMenuItemShortcutProvider
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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
 * Platform integration tests for always-async presentation sampling (cancellation safety, EDT read-lock policy,
 * fresh-factory re-enablement, generation ordering, failed child fail-closed handling, weak-key GC eviction),
 * surface-scoped edit providers & SelectAll for focusable and host-layout-fallback popups, dynamic popup focusable
 * toggling, outbound `Presentation` `IconDescriptor` overrides/resets/runtime templates, and live
 * `KeymapManagerListener` rebinding under [SwingBridgeTheme].
 */
internal class JewelBridgeActionPopupAndSamplingIntegrationTest {
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

        const val KEYMAP_NAME = "JewelBridgeActionPopupAndSamplingKeymap"
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

    @Before
    fun setUp() {
        savedFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        JewelActionMappings.installStandardMappings()
        testDisposable = Disposer.newDisposable("JewelBridgeActionPopupAndSamplingIntegrationTest")
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
            wrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            inner = JTextField()
            wrapper.composePanel.add(inner)
        }
    }

    @After
    fun tearDown() {
        Disposer.dispose(testDisposable)
        KeyboardFocusManager.setCurrentKeyboardFocusManager(savedFocusManager)
        KeymapManagerEx.getInstanceEx().activeKeymap = savedKeymap
        KeymapManagerEx.getInstanceEx().schemeManager.removeScheme(keymap)
        JewelActionMappings.installStandardMappings()
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

    @Test
    fun `cancelled BGT update session never escapes sampledPresentation or scheduler acquire`() {
        val ideActionId = "test.jewel.round7.bgt.cancelledUpdate"
        val jewelId = JewelActionId("test.jewel.round7.mapped.cancelledUpdate")
        val cancellingBgtAction =
            object : AnAction("Cancelled BGT update") {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                override fun update(e: AnActionEvent) {
                    throw java.util.concurrent.CancellationException("simulated cancelled update session")
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(ideActionId, cancellingBgtAction)
        val mapping = JewelActionMappings.map(jewelId, ideActionId)
        val registry = JewelBridgeActionRegistry(actionManager)
        try {
            var directError: Throwable? = null
            runInEdtAndWait { directError = runCatching { registry.sampledPresentation(jewelId) }.exceptionOrNull() }
            registry.awaitPendingSamplesForTest()
            assertNull("CancellationException must not escape sampledPresentation: $directError", directError)

            val host = JewelShortcutHostState(registry) { InMemoryJewelKeymap("round7-cancel") }
            var acquireError: Throwable? = null
            runInEdtAndWait { acquireError = runCatching { host.presentations.acquire(jewelId) }.exceptionOrNull() }
            registry.awaitPendingSamplesForTest()
            assertNull("CancellationException must not escape presentations.acquire: $acquireError", acquireError)
        } finally {
            registry.cancelPendingSamples()
            mapping.close()
            actionManager.unregisterAction(ideActionId)
        }
    }

    @Test
    fun `EDT action requiring read access samples cleanly under lock prohibition and re-enables on fresh PresentationFactory`() {
        val actionId = "test.jewel.round7.edtReadLockAndReenable"
        var allowEnable = false
        var readActionRuns = 0
        val edtReadAction =
            object : AnAction("EDT Read Action") {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    ApplicationManager.getApplication().runReadAction { readActionRuns++ }
                    // Explicitly disable when allowEnable is false; rely on fresh PresentationFactory resetting
                    // isEnabled = true when allowEnable flips back to true.
                    if (!allowEnable) {
                        e.presentation.isEnabled = false
                    }
                    e.presentation.text = if (allowEnable) "Enabled Now" else "Disabled Once"
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, edtReadAction)
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        try {
            val softViolations = mutableListOf<String>()
            runInEdtAndWait {
                val app = ApplicationManager.getApplication()
                val softMethod =
                    runCatching {
                            app.javaClass.getMethod(
                                "withLocksSoftlyProhibited",
                                String::class.java,
                                Consumer::class.java,
                                Supplier::class.java,
                            )
                        }
                        .getOrNull()
                if (softMethod != null) {
                    softMethod.invoke(
                        app,
                        "round7 edt read lock test",
                        Consumer<String> { msg -> softViolations += msg },
                        Supplier { registry.sampledPresentation(JewelActionId(actionId)) },
                    )
                } else {
                    val hardMethod =
                        app.javaClass.getMethod("withLocksProhibited", String::class.java, Supplier::class.java)
                    hardMethod.invoke(
                        app,
                        "round7 edt read lock test",
                        Supplier { registry.sampledPresentation(JewelActionId(actionId)) },
                    )
                }
            }
            assertTrue(
                "sampling on EDT must not trigger lock-prohibition advice: $softViolations",
                softViolations.isEmpty(),
            )
            registry.awaitPendingSamplesForTest()
            val disabledSample = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertNotNull(disabledSample)
            assertEquals("Disabled Once", disabledSample!!.text)
            assertFalse("first update must disable action", disabledSample.enabled)
            assertEquals(1, readActionRuns)

            allowEnable = true
            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            registry.awaitPendingSamplesForTest()
            val reenabledSample = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertNotNull(reenabledSample)
            assertEquals("Enabled Now", reenabledSample!!.text)
            assertTrue(
                "fresh PresentationFactory must reset isEnabled=true on subsequent update",
                reenabledSample.enabled,
            )
            assertEquals(2, readActionRuns)
        } finally {
            registry.cancelPendingSamples()
            actionManager.unregisterAction(actionId)
        }
    }

    @Test
    fun `older async sample completing after newer request never overwrites newer published presentation`() {
        val actionId = "test.jewel.round7.generationOrder"
        val callCounter = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)

        val racingBgtAction =
            object : AnAction("Racing Action") {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                override fun update(e: AnActionEvent) {
                    val call = callCounter.incrementAndGet()
                    if (call == 1) {
                        firstEntered.countDown()
                        try {
                            releaseFirst.await(5, TimeUnit.SECONDS)
                            e.presentation.text = "Stale A"
                            e.presentation.isEnabled = true
                        } finally {
                            firstFinished.countDown()
                        }
                    } else {
                        e.presentation.text = "Fresh B"
                        e.presentation.isEnabled = true
                    }
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }

        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, racingBgtAction)
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        try {
            // Schedule request A and wait until its BGT update() is actively in flight.
            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            assertTrue("first async update must enter update()", firstEntered.await(5, TimeUnit.SECONDS))

            // Schedule newer request B while A is still blocked, then wait for B to publish.
            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            registry.awaitPendingSamplesForTest()
            val afterB = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertEquals("Fresh B", afterB?.text)

            // Let stale request A finish and pump EDT to prove generation gating discards it.
            releaseFirst.countDown()
            assertTrue("first update must exit after release", firstFinished.await(5, TimeUnit.SECONDS))
            registry.awaitPendingSamplesForTest()

            val finalSample = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertEquals("stale async sample A must never overwrite newer sample B", "Fresh B", finalSample?.text)
        } finally {
            releaseFirst.countDown()
            registry.cancelPendingSamples()
            actionManager.unregisterAction(actionId)
        }
    }

    @Test
    fun `unregistering dynamic action allows GC of cached adopted definition and published sample`() {
        val actionId = "test.jewel.round7.weakGcAction"
        val actionManager = ActionManager.getInstance()
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)

        fun registerAndSample(): WeakReference<AnAction> {
            val dynamicAction =
                object : AnAction("Dynamic Action", "Temporary plugin action", AllIcons.Actions.MenuSaveall) {
                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = true
                        e.presentation.text = "Dynamic Live"
                    }

                    override fun actionPerformed(e: AnActionEvent) = Unit
                }
            actionManager.registerAction(actionId, dynamicAction)
            assertNotNull(registry.definition(JewelActionId(actionId)))
            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            registry.awaitPendingSamplesForTest()
            val published = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertEquals("Dynamic Live", published?.text)
            actionManager.unregisterAction(actionId)
            return WeakReference(dynamicAction)
        }

        try {
            val weakRef = registerAndSample()
            // Encourage GC without doing any further lookup on registry (proving weak-key maps release automatically).
            repeat(60) {
                if (weakRef.get() == null) return@repeat
                System.gc()
                Thread.sleep(25)
            }
            assertNull(
                "JewelBridgeActionRegistry must not strongly retain unregistered dynamic AnAction instances",
                weakRef.get(),
            )
        } finally {
            registry.cancelPendingSamples()
            if (actionManager.getAction(actionId) != null) {
                actionManager.unregisterAction(actionId)
            }
        }
    }

    @Test
    fun `editable focusable popup routes Copy Cut Paste and SelectAll to popup surface without firing owner handlers`() {
        val state = JewelShortcutHostState { InMemoryJewelKeymap("round7-surface-scope") }
        lateinit var popupWrapper: JewelComposePanelWrapper
        runInEdtAndWait {
            wrapper.shortcutHostState = state
            popupWrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            popupWrapper.isPopupWrapper = true
            popupWrapper.shortcutHostState = state
        }

        val ownerFocus = FocusRequester()
        val popupFocus = FocusRequester()
        var ownerCopy = 0
        var ownerCut = 0
        var ownerPaste = 0
        var ownerSelectAll = 0
        var popupCopy = 0
        var popupCut = 0
        var popupPaste = 0

        compose.setContent {
            // Owner surface rooted at wrapper.composePanel with focused Copy/Cut/Paste/SelectAll handlers.
            CompositionLocalProvider(LocalComponent provides wrapper.composePanel) {
                Box(state.resolverRootModifier) {
                    Box(
                        Modifier.shortcut(JewelActions.Copy) { ownerCopy++ }
                            .shortcut(JewelActions.Cut) { ownerCut++ }
                            .shortcut(JewelActions.Paste) { ownerPaste++ }
                            .shortcut(JewelActions.SelectAll) { ownerSelectAll++ }
                            .focusRequester(ownerFocus)
                            .focusable()
                    )
                }
            }
            // Focusable popup surface rooted at popupWrapper.composePanel with text-edit semantics only.
            CompositionLocalProvider(LocalComponent provides popupWrapper.composePanel) {
                Box(state.resolverRootModifier) {
                    Box(
                        Modifier.testTag("popupField")
                            .semantics {
                                copyText {
                                    popupCopy++
                                    true
                                }
                                cutText {
                                    popupCut++
                                    true
                                }
                                pasteText {
                                    popupPaste++
                                    true
                                }
                            }
                            .focusRequester(popupFocus)
                            .focusable()
                    )
                }
            }
        }

        // Focus the owner node first so the owner surface genuinely has active focused handlers.
        focusComposeNode(ownerFocus)
        awtFocusOn(inner)

        val removeLane = installBridgeClaimLane(wrapper, state)
        wrapper.pushActivePopupWrapper(popupWrapper)
        try {
            val popupSemanticsNode = compose.onNodeWithTag("popupField").fetchSemanticsNode()
            // Native IDE shortcuts supply the owner focus descendant (inner) because JBPopup keeps AWT focus on the
            // owner; edit providers must still route to popupWrapper.composePanel without running owner handlers.
            val ownerContext =
                com.intellij.openapi.actionSystem.DataContext { dataId ->
                    when {
                        JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> state
                        PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> inner
                        else -> null
                    }
                }
            val seenPanels = mutableListOf<Component>()
            val copyProvider = ComposeCopyProvider { panel ->
                seenPanels += panel
                popupSemanticsNode
            }
            val cutProvider = ComposeCutProvider { panel ->
                seenPanels += panel
                popupSemanticsNode
            }
            val pasteProvider = ComposePasteProvider { panel ->
                seenPanels += panel
                popupSemanticsNode
            }

            runInEdtAndWait {
                assertTrue(copyProvider.isCopyEnabled(ownerContext))
                assertTrue(cutProvider.isCutEnabled(ownerContext))
                assertTrue(pasteProvider.isPasteEnabled(ownerContext))

                copyProvider.performCopy(ownerContext)
                cutProvider.performCut(ownerContext)
                pasteProvider.performPaste(ownerContext)

                // SelectAll bridge must NOT claim Ctrl/Cmd+A for the owner's SelectAll while popup surface is active.
                val selectAllBridge = wrapper.selectAllBridgeAction!!
                val pres = Presentation()
                val event =
                    AnActionEvent.createEvent(
                        selectAllBridge,
                        ownerContext,
                        pres,
                        ActionPlaces.KEYBOARD_SHORTCUT,
                        ActionUiKind.NONE,
                        null,
                    )
                selectAllBridge.update(event)
                assertFalse("owner SelectAll must not be enabled while editable popup is active", pres.isEnabled)
            }

            assertTrue(
                "all edit provider lookups must resolve to the active popup ComposePanel",
                seenPanels.isNotEmpty() && seenPanels.all { it === popupWrapper.composePanel },
            )
            assertEquals("popup copyText semantics must run", 1, popupCopy)
            assertEquals("popup cutText semantics must run", 1, popupCut)
            assertEquals("popup pasteText semantics must run", 1, popupPaste)
            assertEquals("owner Copy handler must NOT fire while popup is active", 0, ownerCopy)
            assertEquals("owner Cut handler must NOT fire while popup is active", 0, ownerCut)
            assertEquals("owner Paste handler must NOT fire while popup is active", 0, ownerPaste)
            assertEquals("owner SelectAll handler must NOT fire while popup is active", 0, ownerSelectAll)
        } finally {
            wrapper.popActivePopupWrapper(popupWrapper)
            removeLane()
        }
    }

    @Test
    fun `toggling focusable on visible popup updates active popup context routing both ways`() {
        val state = JewelShortcutHostState { InMemoryJewelKeymap("round7-focusable-toggle") }
        lateinit var popupWrapper: JewelComposePanelWrapper
        runInEdtAndWait {
            wrapper.shortcutHostState = state
            popupWrapper = JewelComposePanelWrapper(focusOnClickInside = false)
            popupWrapper.isPopupWrapper = true
            popupWrapper.shortcutHostState = state
        }
        awtFocusOn(inner)

        var focusable by mutableStateOf(false)
        compose.setContent {
            ActivePopupWrapperEffect(
                ownerWrapper = wrapper,
                popupWrapper = popupWrapper,
                parentShortcutHost = state,
                focusable = focusable,
                isPopupShown = true,
                isVisible = true,
                useHostLayoutFallback = false,
                isDisposed = false,
            )
        }
        compose.waitForIdle()
        assertSame(
            "non-focusable popup must leave context on owner descendant",
            inner,
            resolveContextComponent(wrapper),
        )

        focusable = true
        compose.waitForIdle()
        assertSame(
            "toggling focusable=true on visible popup must route context to popup ComposePanel",
            popupWrapper.composePanel,
            resolveContextComponent(wrapper),
        )

        focusable = false
        compose.waitForIdle()
        assertSame(
            "toggling focusable=false on visible popup must restore owner context routing",
            inner,
            resolveContextComponent(wrapper),
        )
    }

    @Test
    fun `outbound bridge update and runtime template populate, clear, and restore Presentation IconDescriptor`() {
        val setIconDescriptor =
            runCatching { Presentation::class.java.getMethod("setIconDescriptor", IconDescriptor::class.java) }
                .getOrNull()
        val getIconDescriptor = runCatching { Presentation::class.java.getMethod("getIconDescriptor") }.getOrNull()
        assumeTrue(
            "Presentation.getIconDescriptor/setIconDescriptor unavailable in this SDK",
            setIconDescriptor != null && getIconDescriptor != null,
        )
        val iconDescriptorSetter = checkNotNull(setIconDescriptor)
        val iconDescriptorGetter = checkNotNull(getIconDescriptor)
        val registryValue = runCatching { Registry.get("ide.experimental.icons") }.getOrNull()
        assumeTrue("ide.experimental.icons registry key unavailable", registryValue != null)
        checkNotNull(registryValue).setValue(true, testDisposable)

        val templateDescriptor = AllIcons.Actions.MenuSaveall.toIconDescriptor()
        val overrideDescriptor = AllIcons.Actions.Refresh.toIconDescriptor()

        // 1) Runtime registration populates templatePresentation.getIconDescriptor().
        val runtimeId = JewelActionId("test.jewel.round7.runtimeIconDescriptor")
        val actionManager = ActionManager.getInstance()
        val reg =
            JewelBridgeActionRegistry(actionManager, wrapper)
                .register(
                    JewelActionDefinition(
                        JewelAction(
                            runtimeId,
                            ActionTemplatePresentation(text = "Runtime Icon", icon = templateDescriptor),
                        )
                    )
                )
        try {
            val runtimeAction = checkNotNull(actionManager.getAction(runtimeId.value))
            assertEquals(
                "runtime template must populate Presentation.getIconDescriptor()",
                templateDescriptor,
                iconDescriptorGetter.invoke(runtimeAction.templatePresentation),
            )
        } finally {
            reg.close()
        }

        // 2) Outbound JewelActionBridgeAction.update sets, clears (Set(null)), and restores IconDescriptor on blur.
        val bridgeId = "test.jewel.round7.bridgeIconDescriptor"
        val bridgeAction = JewelActionBridgeAction()
        bridgeAction.templatePresentation.icon = AllIcons.Actions.MenuSaveall
        iconDescriptorSetter.invoke(bridgeAction.templatePresentation, templateDescriptor)
        actionManager.registerAction(bridgeId, bridgeAction)

        val state = JewelShortcutHostState { InMemoryJewelKeymap("round7-icon") }
        runInEdtAndWait { wrapper.shortcutHostState = state }
        val focusNode = FocusRequester()
        var iconOverride by
            mutableStateOf<ActionPresentationOverride>(
                ActionPresentationOverride(icon = PresentationValue.Set(overrideDescriptor))
            )
        compose.setContent {
            Box(state.resolverRootModifier) {
                Box(
                    Modifier.shortcut(
                            JewelAction(JewelActionId(bridgeId), "Bridge Icon"),
                            presentation = iconOverride,
                        ) {}
                        .focusRequester(focusNode)
                        .focusable()
                )
            }
        }
        focusComposeNode(focusNode)
        awtFocusOn(inner)

        try {
            val presentation = bridgeAction.templatePresentation.clone()

            fun runUpdate(component: Component) = runInEdtAndWait {
                val event =
                    AnActionEvent.createEvent(
                        bridgeAction,
                        DataManager.getInstance().getDataContext(component),
                        presentation,
                        ActionPlaces.TOOLBAR,
                        ActionUiKind.TOOLBAR,
                        null,
                    )
                bridgeAction.update(event)
            }

            // Override with Set(overrideDescriptor)
            runUpdate(inner)
            assertEquals(overrideDescriptor, iconDescriptorGetter.invoke(presentation))

            // Override with Set(null) clears both Swing icon and IconDescriptor
            iconOverride = ActionPresentationOverride(icon = PresentationValue.Set(null))
            compose.waitForIdle()
            runUpdate(inner)
            assertNull("Set(null) must clear Swing icon", presentation.icon)
            assertNull(
                "Set(null) must clear Presentation.getIconDescriptor()",
                iconDescriptorGetter.invoke(presentation),
            )

            // Blur restores template IconDescriptor
            val outside = JTextField()
            awtFocusOn(outside)
            runUpdate(outside)
            assertEquals(
                "blur must restore template Presentation.getIconDescriptor()",
                templateDescriptor,
                iconDescriptorGetter.invoke(presentation),
            )
        } finally {
            actionManager.unregisterAction(bridgeId)
        }
    }

    @Test
    fun `live keymap shortcutChanged under SwingBridgeTheme updates LocalMenuItemShortcutProvider immediately`() {
        val initialStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
        val reboundStroke =
            KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        keymap.addShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(initialStroke, null))

        var observedProvider: MenuItemShortcutProvider? = null
        var observedCutStroke: KeyStroke? = null
        compose.setContent {
            SwingBridgeTheme {
                val provider = LocalMenuItemShortcutProvider.current
                observedProvider = provider
                observedCutStroke = provider.getShortcutKeyStroke(ContextMenuItemOptionAction.CutMenuItemOptionAction)
            }
        }
        compose.waitForIdle()
        val firstProvider = checkNotNull(observedProvider)
        assertEquals(initialStroke, observedCutStroke)

        // Mutate active keymap and publish KeymapManagerListener.TOPIC shortcutChanged while UI stays open.
        keymap.removeShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(initialStroke, null))
        keymap.addShortcut(IdeActions.ACTION_CUT, KeyboardShortcut(reboundStroke, null))
        runInEdtAndWait {
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(KeymapManagerListener.TOPIC)
                .shortcutChanged(keymap, IdeActions.ACTION_CUT, false)
        }
        compose.waitForIdle()

        assertNotSame(
            "SwingBridgeTheme must emit a fresh MenuItemShortcutProvider identity on keymap change so open Menu scopes re-register",
            firstProvider,
            observedProvider,
        )
        assertEquals(reboundStroke, observedCutStroke)
    }

    @Test
    fun `host-layout fallback popup Copy binding wins over enclosing owner binding`() {
        val state = JewelShortcutHostState { InMemoryJewelKeymap("fallback-popup-copy") }
        lateinit var popupWrapper: JewelComposePanelWrapper
        runInEdtAndWait {
            wrapper.shortcutHostState = state
            popupWrapper = JewelComposePanelWrapper(focusOnClickInside = false).apply { isPopupWrapper = true }
        }
        var ownerCopy = 0
        var popupCopy = 0
        val popupFocus = FocusRequester()

        compose.setContent {
            CompositionLocalProvider(LocalComponent provides wrapper.composePanel) {
                Box(state.resolverRootModifier) {
                    Box(Modifier.shortcut(JewelActions.Copy) { ownerCopy++ }) {
                        // Models JBPopupRenderer's useHostLayoutFallback branch: popup is not pushed to
                        // activePopupWrappers yet mounts its resolver root under popupWrapper.composePanel.
                        CompositionLocalProvider(LocalComponent provides popupWrapper.composePanel) {
                            Box(state.resolverRootModifier) {
                                Box(
                                    Modifier.shortcut(JewelActions.Copy) { popupCopy++ }
                                        .focusRequester(popupFocus)
                                        .focusable()
                                )
                            }
                        }
                    }
                }
            }
        }
        focusComposeNode(popupFocus)

        val ownerContext =
            com.intellij.openapi.actionSystem.DataContext { dataId ->
                when {
                    JEWEL_SHORTCUT_HOST_STATE.`is`(dataId) -> state
                    PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> wrapper.composePanel
                    else -> null
                }
            }
        runInEdtAndWait { ComposeCopyProvider { null }.performCopy(ownerContext) }

        assertEquals("focused fallback popup binding must run", 1, popupCopy)
        assertEquals("enclosing owner binding must not run", 0, ownerCopy)
    }

    @Test
    fun `failed child update fails closed instead of publishing enabled template presentation`() {
        val actionId = "test.jewel.sampling.failedChildUpdate"
        val failingAction =
            object : AnAction("Failing Action") {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                override fun update(e: AnActionEvent) {
                    error("simulated update failure")
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager = ActionManager.getInstance()
        actionManager.registerAction(actionId, failingAction)
        val registry = JewelBridgeActionRegistry(actionManager, wrapper)
        try {
            runInEdtAndWait { registry.sampledPresentation(JewelActionId(actionId)) }
            registry.awaitPendingSamplesForTest()
            val sampled = registry.withCachedOnlyRead { registry.sampledPresentation(JewelActionId(actionId)) }
            assertTrue(
                "failed child update must never publish an enabled presentation",
                sampled == null || !sampled.enabled,
            )
        } finally {
            registry.cancelPendingSamples()
            actionManager.unregisterAction(actionId)
        }
    }
}

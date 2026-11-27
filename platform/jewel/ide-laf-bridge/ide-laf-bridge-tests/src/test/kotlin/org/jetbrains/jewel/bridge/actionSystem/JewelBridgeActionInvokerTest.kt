// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.ActionCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchRejection
import org.jetbrains.jewel.foundation.shortcut.ActionDispatchResult
import org.jetbrains.jewel.foundation.shortcut.ActionPresentation
import org.jetbrains.jewel.foundation.shortcut.ActionResolution
import org.jetbrains.jewel.foundation.shortcut.ActionTrigger
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins [JewelActionMappings]' explicit `MappedIdeAction` routing and [JewelBridgeActionInvoker]'s dispatch-acceptance
 * contract against a mocked [ActionManager].
 */
internal class JewelBridgeActionInvokerTest {
    private val hostComponent = JPanel()

    @Before
    fun setUp() {
        JewelActionMappings.clearForTests()
    }

    @After
    fun tearDown() {
        JewelActionMappings.clearForTests()
    }

    @Test
    fun `standard mappings install idempotently and never clobber an explicit override`() {
        val customTarget = "MyCustomCopy"
        val actionManager = mockk<ActionManager> { every { getAction(customTarget) } returns mockk<AnAction>() }

        JewelActionMappings.map(JewelActions.Copy.id, customTarget, actionManager)
        JewelActionMappings.installStandardMappings()
        JewelActionMappings.installStandardMappings()

        assertEquals(customTarget, JewelActionMappings.ideActionIdFor(JewelActions.Copy.id))
        assertEquals(IdeActions.ACTION_CUT, JewelActionMappings.ideActionIdFor(JewelActions.Cut.id))
    }

    @Test
    fun `unmapped action IDs resolve to no mapping`() {
        JewelActionMappings.installStandardMappings()
        assertNull(JewelActionMappings.ideActionIdFor(JewelActionId("test.unmapped")))
    }

    @Test
    fun `mapped ID routes to the mapped platform action through tryToExecute`() {
        JewelActionMappings.installStandardMappings()
        val copyAction = mockk<AnAction>()
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(IdeActions.ACTION_COPY) } returns copyAction }

        val result = JewelBridgeActionInvoker(hostComponent, actionManager).invoke(JewelActions.Copy)

        assertEquals(ActionDispatchResult.Dispatched, result)
        verify(exactly = 1) { actionManager.tryToExecute(copyAction, null, hostComponent, any(), true) }
    }

    @Test
    fun `unmapped ID routes to the action registered under the Jewel ID itself`() {
        val jewelAction = JewelAction(JewelActionId("test.jewel.owned"), "Owned")
        val bridgeAction = JewelActionBridgeAction()
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(jewelAction.id.value) } returns bridgeAction }

        val result = JewelBridgeActionInvoker(hostComponent, actionManager).invoke(jewelAction)

        assertEquals(ActionDispatchResult.Dispatched, result)
        verify(exactly = 1) { actionManager.tryToExecute(bridgeAction, null, hostComponent, any(), true) }
    }

    @Test
    fun `unknown target rejects as Unregistered without touching execution`() {
        val jewelAction = JewelAction(JewelActionId("test.jewel.unknown"), "Unknown")
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(jewelAction.id.value) } returns null }

        val result = JewelBridgeActionInvoker(hostComponent, actionManager).invoke(jewelAction)

        assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.Unregistered), result)
        verify(exactly = 0) { actionManager.tryToExecute(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pointer trigger passes null inputEvent, propagates trigger on EDT, and invalidates host on completion`() {
        val jewelAction = JewelAction(JewelActionId("test.jewel.pointer"), "Pointer")
        val bridgeAction = JewelActionBridgeAction()
        val callback = ActionCallback()
        var observedTriggerInsideExecute: PendingBridgeTrigger? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(jewelAction.id.value) } returns bridgeAction
                every { tryToExecute(bridgeAction, null, any(), any(), true) } answers
                    {
                        observedTriggerInsideExecute = bridgeInvokerTrigger.get()
                        callback
                    }
            }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val invalidateCount = AtomicInteger(0)

        val actionPresentationHost =
            JewelShortcutHostState(
                registry =
                    object : org.jetbrains.jewel.foundation.shortcut.JewelActionRegistry {
                        override fun register(
                            definition: org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
                        ) = error("unused")

                        override fun definition(id: JewelActionId) =
                            if (id == jewelAction.id) {
                                org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition(jewelAction)
                            } else {
                                null
                            }

                        override fun definitions() =
                            emptyList<org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition>()

                        override fun sampledPresentation(id: JewelActionId): ActionPresentation {
                            invalidateCount.incrementAndGet()
                            return ActionPresentation(
                                text = "Pointer",
                                enabled = true,
                                resolution = ActionResolution.Resolved,
                            )
                        }
                    }
            ) {
                InMemoryJewelKeymap("empty")
            }
        val sampledFlow = actionPresentationHost.presentations.acquire(jewelAction.id)
        assertTrue(sampledFlow.value.text.isNotEmpty())
        val before = invalidateCount.get()
        wrapper.shortcutHostState = actionPresentationHost

        val result = JewelBridgeActionInvoker(wrapper, actionManager).invoke(jewelAction, ActionTrigger.Pointer)
        assertEquals(ActionDispatchResult.Dispatched, result)
        assertEquals(PendingBridgeTrigger(jewelAction.id.value, ActionTrigger.Pointer), observedTriggerInsideExecute)
        assertNull("thread-local trigger must clear after tryToExecute returns", bridgeInvokerTrigger.get())
        verify(exactly = 1) { actionManager.tryToExecute(bridgeAction, null, wrapper, any(), true) }

        callback.setDone()
        assertTrue("doWhenProcessed must invalidate presentations", invalidateCount.get() > before)
    }

    @Test
    fun `rejected tryToExecute callback distinguishes update-time Disabled from execution-time RejectedByHost`() {
        val jewelAction = JewelAction(JewelActionId("test.jewel.disabled"), "Disabled")
        val bridgeAction = JewelActionBridgeAction()
        val disabledCallback = ActionCallback().apply { reject("action is disabled (early check)") }
        val skippedCallback = ActionCallback().apply { reject("action is skipped") }
        val callbacks = ArrayDeque(listOf(disabledCallback, ActionCallback.REJECTED, skippedCallback))
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(jewelAction.id.value) } returns bridgeAction
                every { tryToExecute(bridgeAction, null, any(), any(), true) } answers { callbacks.removeFirst() }
            }
        val invoker = JewelBridgeActionInvoker(hostComponent, actionManager)

        assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.Disabled), invoker.invoke(jewelAction))
        assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.RejectedByHost), invoker.invoke(jewelAction))
        assertEquals(ActionDispatchResult.Rejected(ActionDispatchRejection.RejectedByHost), invoker.invoke(jewelAction))
    }

    @Test
    fun `JewelActionMappings map returns a handle that restores prior mapping without clobbering newer overrides`() {
        val actionManager =
            mockk<ActionManager> {
                every { getAction("CustomCopy1") } returns mockk<AnAction>()
                every { getAction("CustomCopy2") } returns mockk<AnAction>()
                every { getAction("CustomOther") } returns mockk<AnAction>()
            }
        JewelActionMappings.installStandardMappings()

        val first = JewelActionMappings.map(JewelActions.Copy.id, "CustomCopy1", actionManager)
        assertEquals("CustomCopy1", JewelActionMappings.ideActionIdFor(JewelActions.Copy.id))

        val second = JewelActionMappings.map(JewelActions.Copy.id, "CustomCopy2", actionManager)
        assertEquals("CustomCopy2", JewelActionMappings.ideActionIdFor(JewelActions.Copy.id))

        // Closing the superseded first handle must not clobber the active second override.
        first.close()
        assertEquals("CustomCopy2", JewelActionMappings.ideActionIdFor(JewelActions.Copy.id))

        // Closing the active second handle skips the already-closed first handle and restores $Copy.
        second.close()
        assertEquals(IdeActions.ACTION_COPY, JewelActionMappings.ideActionIdFor(JewelActions.Copy.id))

        // Closing the sole mapping for a non-standard action removes it completely.
        val customId = JewelActionId("test.jewel.custom.mapped")
        val handle = JewelActionMappings.map(customId, "CustomOther", actionManager)
        assertEquals("CustomOther", JewelActionMappings.ideActionIdFor(customId))
        handle.close()
        assertNull(JewelActionMappings.ideActionIdFor(customId))
    }
}

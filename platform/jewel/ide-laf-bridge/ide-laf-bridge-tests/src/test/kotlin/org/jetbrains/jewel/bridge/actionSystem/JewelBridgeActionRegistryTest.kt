// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.input.key.Key
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.jewel.foundation.shortcut.ActionPropertyNames
import org.jetbrains.jewel.foundation.shortcut.ActionTemplatePresentation
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelActionKind
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the attach-or-register lifecycle of [JewelBridgeActionRegistry] against a mocked [ActionManager]: declared
 * (plugin.xml) IDs are attached to and never unregistered; unowned IDs get a runtime action whose lifetime is the
 * reference-counted registration handle; foreign owners are rejected with the mapping hint.
 */
internal class JewelBridgeActionRegistryTest {
    private val actionId = JewelActionId("test.jewel.bridge.action")
    private val definition = JewelActionDefinition(JewelAction(actionId, "Test Action"))

    @Test
    fun `declared bridge action is attached, not re-registered, and closing never unregisters it`() {
        val declared = JewelActionBridgeAction()
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(actionId.value) } returns declared }
        val registry = JewelBridgeActionRegistry(actionManager)

        val registration = registry.register(definition)
        assertEquals(definition, registry.definition(actionId))
        verify(exactly = 0) { actionManager.registerAction(any(), any()) }

        registration.close()
        // Closing drops the explicit registration, but the ID stays resolvable: the registry adopts actions the
        // IDE declares, which is what lets a Jewel control bind an action it never registered. What must not
        // happen is the registry unregistering an action it does not own.
        val adopted = registry.definition(actionId)
        assertNotNull(adopted)
        assertNotEquals(definition, adopted)
        verify(exactly = 0) { actionManager.unregisterAction(any<String>()) }
    }

    @Test
    fun `unowned ID registers a runtime action and the last close unregisters it`() {
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(actionId.value) } answers { registered }
                every { registerAction(actionId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(actionId.value) } answers { registered = null }
            }
        val registry = JewelBridgeActionRegistry(actionManager)

        val first = registry.register(definition)
        val second = registry.register(definition)
        verify(exactly = 1) { actionManager.registerAction(actionId.value, any<JewelActionBridgeAction>()) }

        first.close()
        first.close() // closing is idempotent; the second close must not decrement again
        assertNotNull(registry.definition(actionId))
        verify(exactly = 0) { actionManager.unregisterAction(any<String>()) }

        second.close()
        assertNull(registry.definition(actionId))
        verify(exactly = 1) { actionManager.unregisterAction(actionId.value) }
    }

    @Test
    fun `runtime registration always uses the non dumb-aware base class`() {
        val actionManager = mockk<ActionManager>(relaxed = true) { every { getAction(actionId.value) } returns null }
        val registered = mutableListOf<AnAction>()
        every { actionManager.registerAction(actionId.value, capture(registered)) } returns Unit

        JewelBridgeActionRegistry(actionManager).register(definition)
        assertEquals(JewelActionBridgeAction::class.java, registered.single().javaClass)
    }

    @Test
    fun `differing definition for a registered ID fails`() {
        val actionManager = mockk<ActionManager>(relaxed = true) { every { getAction(actionId.value) } returns null }
        val registry = JewelBridgeActionRegistry(actionManager)
        registry.register(definition)

        assertThrows(IllegalStateException::class.java) {
            registry.register(JewelActionDefinition(JewelAction(actionId, "Different Title")))
        }
    }

    @Test
    fun `an ID owned by a non-Jewel action is rejected towards explicit mappings`() {
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(actionId.value) } returns mockk<AnAction>() }
        val registry = JewelBridgeActionRegistry(actionManager)

        val failure = assertThrows(IllegalStateException::class.java) { registry.register(definition) }
        assertNotNull(failure.message)
        assert(failure.message!!.contains("JewelActionMappings"))
    }

    @Test
    fun `a runtime action shared by two panel registries survives the first registry closing`() {
        var registered: AnAction? = null
        val actionManager = mockk<ActionManager>(relaxed = true)
        every { actionManager.getAction(actionId.value) } answers { registered }
        every { actionManager.registerAction(actionId.value, any()) } answers { registered = secondArg() }
        every { actionManager.unregisterAction(actionId.value) } answers { registered = null }

        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)
        val registrationA = panelA.register(definition)
        val registrationB = panelB.register(definition)
        assertNotNull(actionManager.getAction(actionId.value))
        verify(exactly = 1) { actionManager.registerAction(actionId.value, any<JewelActionBridgeAction>()) }

        registrationA.close()
        assertNotNull(
            "panel B still holds a registration, so the platform action must stay registered",
            actionManager.getAction(actionId.value),
        )
        verify(exactly = 0) { actionManager.unregisterAction(any<String>()) }

        registrationB.close()
        assertNull("the last close unregisters the runtime action", actionManager.getAction(actionId.value))
        verify(exactly = 1) { actionManager.unregisterAction(actionId.value) }
    }

    @Test
    fun `adoption misses are not memoized so later declared actions can still be adopted`() {
        var declared: AnAction? = null
        val actionManager = mockk<ActionManager>(relaxed = true)
        every { actionManager.getAction(actionId.value) } answers { declared }

        val registry = JewelBridgeActionRegistry(actionManager)
        assertNull(registry.definition(actionId))

        declared =
            object : AnAction() {
                init {
                    templatePresentation.setText("Later", false)
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val adopted = registry.definition(actionId)
        assertNotNull("later registered action must adopt after an earlier miss", adopted)
        assertEquals("Later", adopted?.action?.template?.text)
    }

    @Test
    fun `shortcutsFor safely degrades when ApplicationManager is absent`() {
        val actionManager = mockk<ActionManager>(relaxed = true)
        val registry = JewelBridgeActionRegistry(actionManager)
        assertTrue(registry.shortcutsFor(actionId).isEmpty())
    }

    @Test
    fun `runtime registration disables mnemonic parsing on templatePresentation text`() {
        val mnemonicId = JewelActionId("test.jewel.mnemonic.registry")
        val mnemonicDef = JewelActionDefinition(JewelAction(mnemonicId, "Find & Replace_Text"))
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(mnemonicId.value) } answers { registered }
                every { registerAction(mnemonicId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(mnemonicId.value) } answers { registered = null }
            }
        val reg = JewelBridgeActionRegistry(actionManager).register(mnemonicDef)
        try {
            assertNotNull(registered)
            assertEquals("Find & Replace_Text", registered!!.templatePresentation.text)
            assertEquals(-1, registered!!.templatePresentation.displayedMnemonicIndex)
        } finally {
            reg.close()
        }
    }

    @Test
    fun `re-registering after external unregister restores the shared runtime action`() {
        val sharedId = JewelActionId("test.jewel.runtime.reattach")
        val sharedDef = JewelActionDefinition(JewelAction(sharedId, "Reattach"))
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(sharedId.value) } answers { registered }
                every { registerAction(sharedId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(sharedId.value) } answers { registered = null }
            }
        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)
        val regA = panelA.register(sharedDef)
        val initialAction = registered
        assertNotNull(initialAction)

        // Simulate external teardown unregistering the action directly.
        registered = null
        val regB = panelB.register(sharedDef)
        assertTrue("re-registering must restore the shared runtime action instance", registered === initialAction)

        regA.close()
        assertTrue(registered === initialAction)
        regB.close()
        assertNull(registered)
    }

    @Test
    fun `external replacement of runtime action rejects new registration and never unregisters foreign occupant on close`() {
        val replacedId = JewelActionId("test.jewel.runtime.replaced")
        val replacedDef = JewelActionDefinition(JewelAction(replacedId, "Replaced"))
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(replacedId.value) } answers { registered }
                every { registerAction(replacedId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(replacedId.value) } answers { registered = null }
            }
        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)
        val regA = panelA.register(replacedDef)

        val foreignAction = mockk<AnAction>(relaxed = true)
        registered = foreignAction

        val failure = assertThrows(IllegalStateException::class.java) { panelB.register(replacedDef) }
        assertTrue(failure.message!!.contains("JewelActionMappings"))
        assertTrue("foreign occupant must not be overwritten", registered === foreignAction)

        regA.close()
        assertTrue("foreign occupant must not be unregistered when Jewel handles close", registered === foreignAction)
        verify(exactly = 0) { actionManager.unregisterAction(replacedId.value) }
    }

    @Test
    fun `runtime registration with defaultShortcuts logs a warning whereas attaching a declared action does not`() {
        val withDefaultsId = JewelActionId("test.jewel.runtime.defaults")
        val withDefaults =
            JewelActionDefinition(
                JewelAction(withDefaultsId, "Runtime With Defaults"),
                defaultShortcuts = listOf(JewelKeySequence(JewelKeyStroke(Key.G, alt = true))),
            )
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(withDefaultsId.value) } answers { registered }
                every { registerAction(withDefaultsId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(withDefaultsId.value) } answers { registered = null }
            }
        val warnings = mutableListOf<String>()
        val registry = JewelBridgeActionRegistry(actionManager, null) { warnings += it }

        // 1) Runtime registration with non-empty defaultShortcuts logs an actionable warning.
        val reg = registry.register(withDefaults)
        try {
            assertTrue(registered is JewelActionBridgeAction)
            assertEquals(withDefaults, registry.definition(withDefaultsId))
            assertEquals("expected exactly one warning for runtime defaultShortcuts", 1, warnings.size)
            assertTrue("warning must mention plugin.xml", warnings.single().contains("plugin.xml"))
            assertTrue("warning must mention ActionUtil.wrap", warnings.single().contains("ActionUtil.wrap"))
        } finally {
            reg.close()
        }
        assertNull(registered)

        // 2) Runtime registration without defaultShortcuts logs no warning.
        warnings.clear()
        val emptyDefaults = JewelActionDefinition(JewelAction(withDefaultsId, "Runtime Without Defaults"))
        val emptyReg = registry.register(emptyDefaults)
        try {
            assertTrue("empty defaultShortcuts must not warn", warnings.isEmpty())
        } finally {
            emptyReg.close()
        }

        // 3) Attaching to a pre-declared plugin.xml JewelActionBridgeAction with non-empty defaultShortcuts never
        // warns.
        warnings.clear()
        registered = JewelActionBridgeAction()
        val declaredReg = registry.register(withDefaults)
        try {
            assertTrue("declared JewelActionBridgeAction attach must not warn", warnings.isEmpty())
        } finally {
            declaredReg.close()
        }
    }

    @Test
    fun `adoptedDefinitions refreshes when an IDE action is replaced or unregistered`() {
        var current: AnAction? =
            object : AnAction() {
                init {
                    templatePresentation.setText("First Version", false)
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(actionId.value) } answers { current } }
        val registry = JewelBridgeActionRegistry(actionManager)

        assertEquals("First Version", registry.definition(actionId)?.action?.title)

        current =
            object : AnAction() {
                init {
                    templatePresentation.setText("Second Version", false)
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
        assertEquals("Second Version", registry.definition(actionId)?.action?.title)

        current = null
        assertNull("unregistered action must evict stale adopted definition", registry.definition(actionId))
    }

    @Test
    fun `runtime registration copies template presentation and shares its definition across panel registries`() {
        val sharedId = JewelActionId("test.jewel.shared.template")
        val richDefinition =
            JewelActionDefinition(
                JewelAction(
                    id = sharedId,
                    template =
                        ActionTemplatePresentation(
                            text = "Shared Action",
                            description = "Shared description",
                            properties =
                                mapOf(
                                    ActionPropertyNames.ShowTextInToolbar to true,
                                    CustomComponentAction.COMPONENT_KEY.toString() to "must-be-ignored",
                                ),
                        ),
                )
            )
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(sharedId.value) } answers { registered }
                every { registerAction(sharedId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(sharedId.value) } answers { registered = null }
            }
        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)
        val regA = panelA.register(richDefinition)
        try {
            val template = registered!!.templatePresentation
            assertEquals("Shared Action", template.text)
            assertEquals("Shared description", template.description)
            assertEquals(true, template.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))
            assertNull(
                "reserved client property must never be written to templatePresentation",
                template.getClientProperty(CustomComponentAction.COMPONENT_KEY),
            )
            assertEquals(
                "second panel registry must resolve shared runtime definition directly",
                richDefinition,
                panelB.definition(sharedId),
            )
        } finally {
            regA.close()
        }
        assertNull(
            "after last close, second panel registry must no longer resolve runtime definition",
            panelB.definition(sharedId),
        )
    }

    @Test
    fun `declarative attachment shares descriptor across panel registries and detects conflicts`() {
        val declaredId = JewelActionId("jewel.test.sharedDeclared")
        val declared = JewelActionBridgeAction()
        val toggleDef =
            JewelActionDefinition(
                JewelAction(
                    id = declaredId,
                    template = ActionTemplatePresentation(text = "Toggle Feature"),
                    kind = org.jetbrains.jewel.foundation.shortcut.JewelActionKind.Toggle,
                )
            )
        val conflictingDef =
            JewelActionDefinition(
                JewelAction(
                    id = declaredId,
                    template = ActionTemplatePresentation(text = "Conflicting Command"),
                    kind = org.jetbrains.jewel.foundation.shortcut.JewelActionKind.Command,
                )
            )
        var unregisterCount = 0
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(declaredId.value) } returns declared
                every { unregisterAction(declaredId.value) } answers { unregisterCount++ }
            }
        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)

        val regA = panelA.register(toggleDef)
        try {
            assertEquals(
                "second panel registry must see shared declarative Toggle descriptor",
                toggleDef,
                panelB.definition(declaredId),
            )
            assertThrows(IllegalStateException::class.java) { panelB.register(conflictingDef) }
            val regB = panelB.register(toggleDef)
            regA.close()
            assertEquals(
                "shared declarative descriptor must stay active until last handle closes",
                toggleDef,
                panelA.definition(declaredId),
            )
            regB.close()
        } finally {
            regA.close()
        }
        assertEquals("closing declarative attachments must never unregister declared action", 0, unregisterCount)
        assertEquals(
            "after all declarative handles close, definition falls back to adopting declared action",
            org.jetbrains.jewel.foundation.shortcut.JewelActionKind.Command,
            panelB.definition(declaredId)?.action?.kind,
        )
    }

    @Test
    fun `repeat same-registry registration over foreign occupant is rejected and definition does not advertise stale entry`() {
        val foreignId = JewelActionId("test.jewel.runtime.foreignSameRegistry")
        val foreignDef = JewelActionDefinition(JewelAction(foreignId, "Owned"))
        var registered: AnAction? = null
        val actionManager =
            mockk<ActionManager>(relaxed = true) {
                every { getAction(foreignId.value) } answers { registered }
                every { registerAction(foreignId.value, any()) } answers { registered = secondArg() }
                every { unregisterAction(foreignId.value) } answers { registered = null }
            }
        val registry = JewelBridgeActionRegistry(actionManager)
        val first = registry.register(foreignDef)

        val foreign = mockk<AnAction>(relaxed = true)
        registered = foreign

        assertThrows(IllegalStateException::class.java) { registry.register(foreignDef) }
        assertTrue("foreign occupant must remain untouched", registered === foreign)
        assertNull("stale Jewel definition must not be returned for a foreign occupant", registry.definition(foreignId))

        first.close()
        verify(exactly = 0) { actionManager.unregisterAction(foreignId.value) }
    }

    @Test
    fun `closing an old declarative attachment handle never releases a replacement declaration`() {
        val reloadId = JewelActionId("jewel.test.reloadedDeclared")
        var currentDeclared: AnAction = JewelActionBridgeAction()
        val defV1 =
            JewelActionDefinition(
                JewelAction(reloadId, ActionTemplatePresentation("V1"), kind = JewelActionKind.Toggle)
            )
        val defV2 =
            JewelActionDefinition(
                JewelAction(reloadId, ActionTemplatePresentation("V2"), kind = JewelActionKind.Toggle)
            )
        val actionManager =
            mockk<ActionManager>(relaxed = true) { every { getAction(reloadId.value) } answers { currentDeclared } }
        val panelA = JewelBridgeActionRegistry(actionManager)
        val panelB = JewelBridgeActionRegistry(actionManager)
        val panelC = JewelBridgeActionRegistry(actionManager)

        val regA = panelA.register(defV1)
        // Simulate plugin reload replacing the declared JewelActionBridgeAction instance under the same ID.
        currentDeclared = JewelActionBridgeAction()
        val regB = panelB.register(defV2)
        try {
            regA.close()
            assertEquals(
                "closing old attachment must not decrement replacement declaration refCount",
                defV2,
                panelC.definition(reloadId),
            )
        } finally {
            regB.close()
        }
    }

    @Test
    fun `closing a JewelActionMappings override installed before installStandardMappings restores standard default`() {
        JewelActionMappings.clearForTests()
        try {
            val actionManager = mockk<ActionManager> { every { getAction("PluginCopy") } returns mockk<AnAction>() }
            val handle =
                JewelActionMappings.map(
                    org.jetbrains.jewel.foundation.shortcut.JewelActions.Copy.id,
                    "PluginCopy",
                    actionManager,
                )
            JewelActionMappings.installStandardMappings()
            assertEquals(
                "PluginCopy",
                JewelActionMappings.ideActionIdFor(org.jetbrains.jewel.foundation.shortcut.JewelActions.Copy.id),
            )

            handle.close()
            assertEquals(
                com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY,
                JewelActionMappings.ideActionIdFor(org.jetbrains.jewel.foundation.shortcut.JewelActions.Copy.id),
            )
        } finally {
            JewelActionMappings.clearForTests()
        }
    }
}

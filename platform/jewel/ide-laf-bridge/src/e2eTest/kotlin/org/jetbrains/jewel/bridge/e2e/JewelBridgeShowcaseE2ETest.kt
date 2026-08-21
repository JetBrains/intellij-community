// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSpectreAgentApi::class)

package org.jetbrains.jewel.bridge.e2e

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.Starter
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.tools.ide.starter.product.idea.community.IdeaCommunity
import dev.sebastiano.spectre.agent.AgentAttach
import dev.sebastiano.spectre.agent.AttachOptions
import dev.sebastiano.spectre.agent.ExperimentalSpectreAgentApi
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.e2e.PopupScenarios
import org.junit.jupiter.api.Test

/**
 * Runs the shared showcase scenarios against Jewel as the IDE hosts it. For the popup scenarios that makes
 * `JBPopupRenderer` the renderer under test.
 *
 * Nothing cheaper reaches it. It builds a real `JBPopup` through `JBPopupFactory`, so it needs a running application
 * and a display, and before this lane existed reverting the whole file left every test that compiles the bridge module
 * green.
 *
 * The IDE is a child process: the starter builds one from sources and launches it, and Spectre attaches to it over the
 * JDK Attach API. This test JVM stays headless, which is what lets it be an ordinary `jps_test`. Attaching also keeps
 * Spectre out of the IDE — the agent's inject bootstrap carries what it needs into the target, so no Jewel artifact
 * ever ships it.
 *
 * The driver boots the IDE, opens the showcase dialog by action id, and presses keys. Everything else that touches
 * Compose content goes through Spectre, which addresses nodes from the semantics tree in the target process rather than
 * by screen coordinates, and so is not subject to the driver's Compose coordinate mapping. Keys are the exception
 * because Spectre's attach input never enters the IDE's event queue; see [AttachedShowcaseAutomator].
 */
class JewelBridgeShowcaseE2ETest {
    @Test
    fun `the showcase behaves in the IDE`() {
        AdditionalModulesForDevBuildServer.addAdditionalModules("intellij.devkit")
        val projectDir = Files.createTempDirectory("jewel-bridge-e2e")
        projectDir.resolve(".idea").createDirectories()

        val ideInfo = IdeInfo.IdeaCommunity.copy(platformPrefix = "community")
        val context = Starter.newContext("jewel-bridge-e2e", TestCase(ideInfo, LocalProjectInfo(projectDir)))
        context.applyVMOptionsPatch {
            // The renderer under test. Without this Jewel uses Compose's own popups, which the standalone lane
            // already compares against.
            addSystemProperty("jewel.customPopupRender", "true")
            // JEP 451: without it a JDK 21+ target warns on dynamic agent loading, and a later JDK may refuse it.
            addLine("-XX:+EnableDynamicAgentLoading", "-XX:+EnableDynamicAgentLoading")
        }

        val failures = mutableListOf<String>()
        val run = context.runIdeWithDriver(runTimeout = 20.minutes)
        val idePid = run.process.id.toLong()

        run.useDriverAndCloseIde {
            waitForProjectOpen()
            invokeAction("JewelComponentShowcaseDialog", now = false)

            // Attaching before the dialog exists fails: the agent looks for a Compose host in the target and finds
            // none, because the IDE has not loaded Compose yet. `invokeAction` is asynchronous, so wait for the
            // Jewel panel to appear first. This only reads the component tree; the pointer targeting that the
            // driver gets wrong for Compose is never used.
            waitFor("the showcase dialog is up", 90.seconds) {
                ui.xx { byClass("JewelComposePanelWrapper") }.list().isNotEmpty()
            }

            // The keys pressed below are real OS input. The probe presses a harmless key and checks that the IDE's
            // event queue saw it, so a wrong foreground window or input desktop fails here by name, rather than
            // later as a popup that "stayed open".
            waitFor("the IDE to receive OS keyboard input", 30.seconds) { ui.robot.hasInputFocus() }

            AgentAttach.attach(idePid, AttachOptions(agentJarPath = agentRuntimeJar())).use { attached ->
                // Keys go through the driver's robot; see AttachedShowcaseAutomator.
                val automator =
                    AttachedShowcaseAutomator(attached, pressOsKey = { keyCode -> ui.keyboard { key(keyCode) } })
                runBlocking { PopupScenarios.runAll(automator, failures) }
            }
        }

        assertEquals(emptyList(), failures, "The showcase misbehaved in the IDE")
    }
}

/**
 * The absolute path of the loadable agent jar.
 *
 * Spectre finds it on the attacher's classpath by default, but that does not survive Bazel: the entry is a
 * runfiles-relative path, and `VirtualMachine.loadAgent` resolves paths in the *target* process, which has a different
 * working directory. On Windows it is not on `java.class.path` at all, because Bazel collapses a long classpath into a
 * single manifest-only jar. The lane therefore passes the runfiles path in, and it is resolved here.
 */
private fun agentRuntimeJar(): Path {
    val rlocationPath =
        System.getProperty("jewel.spectre.agentRuntimeJar")
            ?: error("jewel.spectre.agentRuntimeJar is unset; the lane has to pass the agent jar's runfiles path")
    return BazelRunfiles.resolveRunfilePath(rlocationPath).toAbsolutePath().normalize()
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.Text
import org.junit.jupiter.api.Test

// Headful, standalone-only. Spectre cannot see the IJP bridge or the Darcula LaF (JEWEL-1397).
//   bazel test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-spectre-tests
//
// Assertions are on framebuffer pixels in each control's bounds. Compose state alone would not
// catch a missing Error/Warning outline.
class ValidationOutlineSpectreTest {
    @Test fun `light Int UI paints checkbox and radio validation outlines`(): Unit = assertOutlines(isDark = false)

    @Test fun `dark Int UI paints checkbox and radio validation outlines`(): Unit = assertOutlines(isDark = true)
}

private fun assertOutlines(isDark: Boolean): Unit = runSpectreTest {
    val app = ValidationOutlineApplication(isDark)
    app.start()
    try {
        val window = app.awaitWindow()
        window.isAlwaysOnTop = true
        window.toFront()
        val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(window))
        waitForTaggedNode(automator, READY_TAG)
        automator.waitForIdle()

        val screenshotDir = System.getenv("JEWEL_SPECTRE_SCREENSHOT_DIR")
        if (!screenshotDir.isNullOrBlank()) {
            val file = File(screenshotDir, if (isDark) "validation-dark.png" else "validation-light.png")
            file.parentFile.mkdirs()
            ImageIO.write(automator.screenshot(window.bounds), "png", file)
        }

        val shots = SPECS.associate { spec -> spec.tag to screenshotControl(automator, spec.tag) }
        for (spec in SPECS) {
            if (spec.outline == Outline.None) continue
            val baseline =
                SPECS.single {
                    it.kind == spec.kind &&
                        it.checked == spec.checked &&
                        it.enabled == spec.enabled &&
                        it.outline == Outline.None
                }
            val diff = shots.getValue(spec.tag).pixelDiffCount(shots.getValue(baseline.tag))
            assertTrue(
                diff >= MIN_OUTLINE_PIXELS,
                "${spec.tag} should differ from ${baseline.tag} when ${spec.outline} is painted (diff=$diff)",
            )
        }
    } finally {
        app.stop()
    }
}

@Composable
private fun ValidationOutlineScreen() {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ready", modifier = Modifier.testTag(READY_TAG))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (spec in SPECS.filter { it.kind == Kind.Checkbox }) {
                Box(Modifier.testTag(spec.tag)) {
                    Checkbox(
                        checked = spec.checked,
                        onCheckedChange = {},
                        enabled = spec.enabled,
                        outline = spec.outline,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (spec in SPECS.filter { it.kind == Kind.Radio }) {
                Box(Modifier.testTag(spec.tag)) {
                    RadioButton(selected = spec.checked, onClick = {}, enabled = spec.enabled, outline = spec.outline)
                }
            }
        }
    }
}

private class ValidationOutlineApplication(private val isDark: Boolean) {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)

    fun start() {
        thread(name = "spectre-validation-outline-window", isDaemon = true) {
            application(exitProcessOnExit = false) {
                exitApplication.set(::exitApplication)
                JewelWindow(
                    onCloseRequest = ::exitApplication,
                    state = rememberWindowState(size = DpSize(520.dp, 220.dp)),
                    title = "Jewel Spectre validation outline ${if (isDark) "dark" else "light"}",
                    alwaysOnTop = true,
                ) {
                    this@ValidationOutlineApplication.window.compareAndSet(null, window)
                    IntUiTheme(isDark = isDark) {
                        Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                            ValidationOutlineScreen()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        exitApplication.get()?.invoke()
    }

    suspend fun awaitWindow(): ComposeWindow {
        repeat(100) {
            window.get()?.let {
                return it
            }
            delay(100.milliseconds)
        }
        error("The Compose test window was not created")
    }
}

private suspend fun waitForTaggedNode(automator: ComposeAutomator, tag: String) =
    try {
        automator.waitForNode(tag = tag, timeout = 20.seconds)
    } catch (e: TimeoutCancellationException) {
        throw AssertionError("Missing testTag=$tag. Tree:\n${automator.printTree()}", e)
    }

private suspend fun screenshotControl(automator: ComposeAutomator, tag: String): BufferedImage {
    val node = waitForTaggedNode(automator, tag)
    return automator.screenshot(node.boundsOnScreen)
}

private fun BufferedImage.pixelDiffCount(other: BufferedImage): Int {
    val width = minOf(width, other.width)
    val height = minOf(height, other.height)
    var count = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (getRGB(x, y) != other.getRGB(x, y)) count++
        }
    }
    return count
}

private enum class Kind {
    Checkbox,
    Radio,
}

private class Spec(val tag: String, val kind: Kind, val outline: Outline, val checked: Boolean, val enabled: Boolean)

private val SPECS =
    listOf(
        Spec("checkbox.none.off", Kind.Checkbox, Outline.None, checked = false, enabled = true),
        Spec("checkbox.none.on", Kind.Checkbox, Outline.None, checked = true, enabled = true),
        Spec("checkbox.none.disabled", Kind.Checkbox, Outline.None, checked = false, enabled = false),
        Spec("checkbox.error.off", Kind.Checkbox, Outline.Error, checked = false, enabled = true),
        Spec("checkbox.error.on", Kind.Checkbox, Outline.Error, checked = true, enabled = true),
        Spec("checkbox.error.disabled", Kind.Checkbox, Outline.Error, checked = false, enabled = false),
        Spec("checkbox.warning.off", Kind.Checkbox, Outline.Warning, checked = false, enabled = true),
        Spec("checkbox.warning.on", Kind.Checkbox, Outline.Warning, checked = true, enabled = true),
        Spec("checkbox.warning.disabled", Kind.Checkbox, Outline.Warning, checked = false, enabled = false),
        Spec("radio.none.off", Kind.Radio, Outline.None, checked = false, enabled = true),
        Spec("radio.none.on", Kind.Radio, Outline.None, checked = true, enabled = true),
        Spec("radio.none.disabled", Kind.Radio, Outline.None, checked = false, enabled = false),
        Spec("radio.error.off", Kind.Radio, Outline.Error, checked = false, enabled = true),
        Spec("radio.error.on", Kind.Radio, Outline.Error, checked = true, enabled = true),
        Spec("radio.error.disabled", Kind.Radio, Outline.Error, checked = false, enabled = false),
        Spec("radio.warning.off", Kind.Radio, Outline.Warning, checked = false, enabled = true),
        Spec("radio.warning.on", Kind.Radio, Outline.Warning, checked = true, enabled = true),
        Spec("radio.warning.disabled", Kind.Radio, Outline.Warning, checked = false, enabled = false),
    )

private const val READY_TAG = "spectre.validation.ready"
private const val MIN_OUTLINE_PIXELS = 8

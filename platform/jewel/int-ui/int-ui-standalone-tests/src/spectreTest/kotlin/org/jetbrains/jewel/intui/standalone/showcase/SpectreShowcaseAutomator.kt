// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.showcase

import dev.sebastiano.spectre.core.ComposeAutomator
import org.jetbrains.jewel.e2e.ShowcaseAutomator

/**
 * Backs the shared popup scenarios with Spectre's in-process automator, against the standalone sample.
 *
 * The IDE lane backs the same scenarios with Spectre attached to a running IDE. Keeping both behind one interface is
 * what stops the two hosts drifting: a popup fix that only lands for one of them fails here.
 */
internal class SpectreShowcaseAutomator(private val automator: ComposeAutomator) : ShowcaseAutomator {
    override suspend fun isPresent(tag: String): Boolean {
        automator.refreshWindows()
        return automator.findByTestTag(tag).isNotEmpty()
    }

    override suspend fun visibleTags(): List<String> {
        automator.refreshWindows()
        return automator.allNodes().mapNotNull { it.testTag }.distinct().sorted()
    }

    override suspend fun click(tag: String) {
        automator.refreshWindows()
        val node = automator.findByTestTag(tag).firstOrNull() ?: error("Nothing tagged '$tag' to click")
        automator.click(node)
    }

    override suspend fun pressKey(keyCode: Int) {
        automator.pressKey(keyCode)
    }

    override suspend fun waitForIdle() {
        automator.waitForIdle()
    }

    override suspend fun clickByContentDescription(description: String) {
        waitUntil("a node described as '$description' to appear") {
            automator.refreshWindows()
            automator.findByContentDescription(description).isNotEmpty()
        }
        automator.click(automator.findByContentDescription(description).first())
    }
}

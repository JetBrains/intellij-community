// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.showcase

import dev.sebastiano.spectre.core.ComposeAutomator
import org.jetbrains.jewel.e2e.ShowcaseAutomator

/**
 * Backs the shared popup scenarios with Spectre's in-process automator, against the standalone sample.
 *
 * The IDE lane backs the same scenarios with Spectre attached to a running IDE. Keeping both behind one interface is
 * what stops the two hosts drifting.
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

    override suspend fun scrollWheel(tag: String, wheelClicks: Int) {
        automator.refreshWindows()
        val node = automator.findByTestTag(tag).firstOrNull() ?: error("Nothing tagged '$tag' to scroll")
        automator.scrollWheel(node, wheelClicks)
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
        waitUntil("a node described as '$description' to have visible bounds") {
            automator.refreshWindows()
            val target = automator.findByContentDescription(description).firstOrNull() ?: return@waitUntil false
            val targetBounds = target.boundsOnScreen
            if (targetBounds.width > MIN_CLIPPED_BOUNDS_PX && targetBounds.height > MIN_CLIPPED_BOUNDS_PX) {
                return@waitUntil true
            }

            val nodes = automator.allNodes()
            val targetIndex = nodes.indexOfFirst { it.key == target.key }
            val visibleToolbarNode =
                nodes.firstOrNull {
                    val bounds = it.boundsOnScreen
                    it.contentDescription?.startsWith("Show ") == true &&
                        bounds.width > MIN_CLIPPED_BOUNDS_PX &&
                        bounds.height > MIN_CLIPPED_BOUNDS_PX
                } ?: return@waitUntil false
            val visibleIndex = nodes.indexOfFirst { it.key == visibleToolbarNode.key }
            val wheelClicks = if (targetIndex >= visibleIndex) 5 else -5
            automator.scrollWheel(visibleToolbarNode, wheelClicks)
            automator.waitForIdle()
            automator.refreshWindows()
            val updatedBounds = automator.findByContentDescription(description).firstOrNull()?.boundsOnScreen
            updatedBounds != null &&
                updatedBounds.width > MIN_CLIPPED_BOUNDS_PX &&
                updatedBounds.height > MIN_CLIPPED_BOUNDS_PX
        }
        automator.click(automator.findByContentDescription(description).first())
    }
}

// Spectre clamps empty clipped bounds to 1x1 px in composeBoundsToAwtRectangle; require more than that
// so partially or fully clipped nodes are scrolled into view before clicking.
private const val MIN_CLIPPED_BOUNDS_PX = 4

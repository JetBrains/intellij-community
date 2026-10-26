// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSpectreAgentApi::class)

package org.jetbrains.jewel.bridge.e2e

import dev.sebastiano.spectre.agent.AttachedAutomator
import dev.sebastiano.spectre.agent.ExperimentalSpectreAgentApi
import org.jetbrains.jewel.e2e.ShowcaseAutomator

/**
 * Backs the shared popup scenarios with Spectre attached to a running IDE.
 *
 * Keys are not Spectre's to press here. Since 0.6.0 the attach path drives synthetic AWT input, which hands a key event
 * straight to the Compose host's peer and never enters `IdeEventQueue`. That is where an open `JBPopup` gets first
 * refusal of the key, so the bridge renderer's Escape handling, the very thing the JEWEL-1396 scenario exists to
 * observe, would never run; the ComboBox would close itself and the lane would pass with the renderer broken. The key
 * therefore comes in at the OS level through [pressOsKey] and takes the same route as a user's.
 */
internal class AttachedShowcaseAutomator(
    private val attached: AttachedAutomator,
    private val pressOsKey: (keyCode: Int) -> Unit,
) : ShowcaseAutomator {
    override suspend fun isPresent(tag: String): Boolean = attached.findByTestTag(tag).isNotEmpty()

    override suspend fun visibleTags(): List<String> = attached.allNodes().mapNotNull { it.testTag }.distinct().sorted()

    override suspend fun click(tag: String) {
        val node = attached.findByTestTag(tag).firstOrNull() ?: error("Nothing tagged '$tag' to click")
        attached.click(node)
    }

    override suspend fun scrollWheel(tag: String, wheelClicks: Int) {
        val node = attached.findByTestTag(tag).firstOrNull() ?: error("Nothing tagged '$tag' to scroll")
        attached.scrollWheel(node.key, wheelClicks)
    }

    override suspend fun pressKey(keyCode: Int) {
        pressOsKey(keyCode)
    }

    override suspend fun waitForIdle() {
        attached.waitForIdle()
    }

    override suspend fun clickByContentDescription(description: String) {
        waitUntil("a node described as '$description' to appear") {
            attached.findByContentDescription(description).isNotEmpty()
        }
        waitUntil("a node described as '$description' to have visible bounds") {
            val target = attached.findByContentDescription(description).firstOrNull() ?: return@waitUntil false
            if (target.bounds.width > MIN_CLIPPED_BOUNDS_PX && target.bounds.height > MIN_CLIPPED_BOUNDS_PX) {
                return@waitUntil true
            }

            val nodes = attached.allNodes()
            val targetIndex = nodes.indexOfFirst { it.key == target.key }
            val visibleToolbarNode =
                nodes.firstOrNull {
                    it.contentDescription?.startsWith("Show ") == true &&
                        it.bounds.width > MIN_CLIPPED_BOUNDS_PX &&
                        it.bounds.height > MIN_CLIPPED_BOUNDS_PX
                } ?: return@waitUntil false
            val visibleIndex = nodes.indexOfFirst { it.key == visibleToolbarNode.key }
            val wheelClicks = if (targetIndex >= visibleIndex) 5 else -5
            attached.scrollWheel(visibleToolbarNode.key, wheelClicks)
            attached.waitForIdle()
            val updated = attached.findByContentDescription(description).firstOrNull()
            updated != null &&
                updated.bounds.width > MIN_CLIPPED_BOUNDS_PX &&
                updated.bounds.height > MIN_CLIPPED_BOUNDS_PX
        }
        attached.click(attached.findByContentDescription(description).first())
    }
}

// Spectre clamps empty clipped bounds to 1x1 px in composeBoundsToAwtRectangle; require more than that
// so partially or fully clipped nodes are scrolled into view before clicking.
private const val MIN_CLIPPED_BOUNDS_PX = 4

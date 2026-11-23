// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

/**
 * The action place Jewel reports when it updates or executes a platform action.
 *
 * A dedicated place — rather than `ActionPlaces.UNKNOWN`, which literally means "no idea who is asking" — lets actions
 * and telemetry recognise Jewel as the caller. Both the presentation update and the execution report it, so an action
 * never sees one place while being updated and a different one while running.
 */
internal const val JEWEL_ACTION_PLACE: String = "JewelActionComponent"

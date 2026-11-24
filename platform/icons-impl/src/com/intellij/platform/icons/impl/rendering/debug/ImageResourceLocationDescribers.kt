// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.debug

import com.intellij.platform.icons.ImageResourceLocation
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Debug-only, display-only view of one concrete [ImageResourceLocation] type, so icon debugging tools can show all of a
 * location's fields without the location classes themselves having to expose anything. Register implementations in
 * [ImageResourceLocationDescribers].
 */
@ApiStatus.Internal
interface ImageResourceLocationDescriber<T : ImageResourceLocation> {
  val locationClass: Class<T>

  /** All fields to show in the inspector, in display order, e.g. `listOf("path" to ..., "pluginId" to ...)`. */
  fun describe(location: T): List<Pair<String, String?>>
}

/**
 * Registry of [ImageResourceLocationDescriber]s. Thread-safe.
 */
@ApiStatus.Internal
object ImageResourceLocationDescribers {
  private val describers = CopyOnWriteArrayList<ImageResourceLocationDescriber<*>>()

  /** Registers [describer]; closing the returned handle unregisters it. */
  fun register(describer: ImageResourceLocationDescriber<*>): AutoCloseable {
    describers.add(describer)
    return AutoCloseable { describers.remove(describer) }
  }

  /**
   * The describer for [location]: one registered for exactly its class if any, otherwise the first one whose
   * [ImageResourceLocationDescriber.locationClass] is a supertype of it, otherwise `null`.
   */
  fun find(location: ImageResourceLocation): ImageResourceLocationDescriber<*>? {
    val locationClass = location.javaClass
    return describers.firstOrNull { it.locationClass == locationClass }
           ?: describers.firstOrNull { it.locationClass.isAssignableFrom(locationClass) }
  }

  /** Fields of [location] for display; without a registered describer, its class name and `toString()`. */
  fun describe(location: ImageResourceLocation): List<Pair<String, String?>> {
    val describer = find(location)
                    ?: return listOf("class" to location.javaClass.name, "toString" to location.toString())
    return asTyped(describer).describe(location)
  }

  // safe: find() only returns describers whose locationClass accepts the location
  @Suppress("UNCHECKED_CAST")
  private fun asTyped(describer: ImageResourceLocationDescriber<*>): ImageResourceLocationDescriber<ImageResourceLocation> =
    describer as ImageResourceLocationDescriber<ImageResourceLocation>
}

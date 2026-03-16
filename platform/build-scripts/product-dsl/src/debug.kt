// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NOTHING_TO_INLINE")

package org.jetbrains.intellij.build.productLayout

/**
 * Debug output filter set by [GeneratorRunOptions.logFilter] during generator startup.
 * - null = debug disabled
 * - emptySet = all debug output enabled
 * - non-empty set = only matching tags enabled
 */
@Volatile
private var logFilter: Set<String>? = null

fun setProductDslLogFilter(filter: Set<String>?) {
  logFilter = filter
}

/** Untagged debug output - prints when any debug is enabled */
internal inline fun debug(message: () -> String) {
  if (logFilter != null) {
    println("[DEBUG] ${message()}")
  }
}

/** Tagged debug output - prints when tag matches filter or filter is "*" */
internal inline fun debug(tag: String, message: () -> String) {
  val filter = logFilter
  if (filter != null && (filter.isEmpty() || tag in filter)) {
    println("[DEBUG:$tag] ${message()}")
  }
}

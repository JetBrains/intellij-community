// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.ide.IdeBundle
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.text.NaturalComparator
import org.jetbrains.annotations.Nls

/**
 * Joins the readable names of the given [systemIds] into a single human-readable string.
 *
 * Duplicates are collapsed, the names are sorted in natural order and combined with a conjunction,
 * e.g. `Gradle`, `Gradle and Maven` or `Gradle, Maven, and sbt`.
 *
 * This is a simple, dependency-free counterpart of the icu-based joining, that can be used early in startup and from EDT.
 */
fun naturalJoinSystemIds(systemIds: Collection<ProjectSystemId>): @Nls String {
  val names = systemIds.asSequence()
    .map { it.readableName }
    .distinct()
    .sortedWith(NaturalComparator.INSTANCE)
    .toList()
  return when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> IdeBundle.message("external.system.ids.join.two", names[0], names[1])
    else -> IdeBundle.message(
      "external.system.ids.join.more",
      names.subList(0, names.size - 1).joinToString(separator = ", "),
      names.last(),
    )
  }
}

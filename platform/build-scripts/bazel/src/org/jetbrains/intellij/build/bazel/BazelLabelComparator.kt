// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

class BazelLabelComparator(
  val forLoadStatements: Boolean = false,
) : Comparator<String> {

  override fun compare(a: String, b: String): Int {
    return groupComparator
      .then(labelPartsComparator)
      .thenBy { it }
      .compare(a, b)
  }

  private val groupComparator = Comparator<String> { a, b ->
    val direction = if (forLoadStatements) -1 else 1
    getGroup(a).compareTo(getGroup(b)) * direction
  }

  private fun getGroup(s: String) = when {
    s.startsWith("@") -> 3
    s.startsWith("//") -> 2
    s.startsWith(":") -> 1
    else -> 0
  }

  private val labelPartsComparator = Comparator<String> { a, b ->
    val aLabelParts = labelParts(a)
    val bLabelParts = labelParts(b)

    for (i in 0 until minOf(aLabelParts.size, bLabelParts.size)) {
      val compareResult = aLabelParts[i].compareTo(bLabelParts[i])
      if (compareResult != 0) return@Comparator compareResult
    }

    // Shorter label wins if all shared parts are equal
    aLabelParts.size.compareTo(bLabelParts.size)
  }

  private fun labelParts(s: String): List<String> {
    return s.replace(':', '.')
      .split('.')
  }
}

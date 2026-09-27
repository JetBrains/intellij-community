@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import java.text.Normalizer
import java.util.Locale

@ApiStatus.Internal
fun devBuildPathIdentity(path: String): String {
  val folded = Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT).uppercase(Locale.ROOT).lowercase(Locale.ROOT)
  return Normalizer.normalize(folded, Normalizer.Form.NFC)
}

@ApiStatus.Internal
fun validateDevBuildDirectorySpellings(paths: Collection<String>) {
  val directories = HashMap<String, String>()
  for (path in paths) {
    var parent = path
    while (parent.isNotEmpty()) {
      val previous = directories.putIfAbsent(devBuildPathIdentity(parent), parent)
      check(previous == null || previous == parent) { "Conflicting destination spellings '$previous' and '$parent'" }
      parent = parent.substringBeforeLast('/', "")
    }
  }
}

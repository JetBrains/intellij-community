@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
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

@ApiStatus.Internal
fun validateDevBuildLinks(entries: List<Pair<String, String>>): Map<String, String> {
  val links = HashMap<String, Pair<String, String>>()
  val resolvedLinks = HashMap<String, List<String>>()
  for ((path, target) in entries) {
    checkDevBuildDistributionLink(path, target)
    check(links.putIfAbsent(devBuildPathIdentity(path), path to target) == null) {
      "Duplicate distribution link '$path'"
    }
  }
  fun resolve(parts: List<String>, stack: MutableList<String>, active: MutableSet<String>) {
    for (part in parts) {
      when (part) {
        "", "." -> continue
        ".." -> {
          check(stack.isNotEmpty()) { "Distribution link chain escapes the distribution" }
          stack.removeAt(stack.lastIndex)
        }
        else -> {
          stack.add(part)
          val identity = devBuildPathIdentity(stack.joinToString("/"))
          val link = links.get(identity) ?: continue
          val cached = resolvedLinks.get(identity)
          if (cached != null) {
            stack.clear()
            stack.addAll(cached)
            continue
          }
          check(active.add(identity)) { "Distribution link cycle at '${link.first}'" }
          stack.clear()
          stack.addAll(link.first.split('/').dropLast(1))
          resolve(link.second.split('/'), stack, active)
          active.remove(identity)
          resolvedLinks.put(identity, stack.toList())
        }
      }
    }
  }
  val destinations = HashMap<String, String>(links.size)
  for (link in links.values) {
    val destination = ArrayList<String>()
    resolve(link.first.split('/'), destination, HashSet())
    destinations.put(link.first, destination.joinToString("/"))
  }
  return destinations
}

@ApiStatus.Internal
fun checkDevBuildDistributionLink(path: String, symlinkTarget: String) {
  validateDevBuildLocalPath(path)
  val link = Path.of(symlinkTarget)
  val destination = Path.of(path).parent?.resolve(link) ?: link
  check(symlinkTarget.isNotEmpty() && !link.isAbsolute && symlinkTarget.none { it == '\\' || it == ':' || it == '\u0000' } &&
        !destination.normalize().startsWith("..")) {
    "Dev-build component symbolic link '$path' escapes the distribution: $symlinkTarget"
  }
}

@ApiStatus.Internal
fun validateDevBuildLocalPath(path: String) {
  check(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' } &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
    "Invalid path in the local dev layout: $path"
  }
}

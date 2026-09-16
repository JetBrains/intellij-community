package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

@ApiStatus.Internal
val commonModuleExcludes: List<PathMatcher> = FileSystems.getDefault().let { fs ->
  listOf(
    fs.getPathMatcher("glob:**/icon-robots.txt"),
    fs.getPathMatcher("glob:icon-robots.txt"),
    fs.getPathMatcher("glob:.unmodified"),
    // compilation cache on TC
    fs.getPathMatcher("glob:.hash"),
    fs.getPathMatcher("glob:classpath.index"),
    fs.getPathMatcher("glob:module-info.class"),
  )
}

@ApiStatus.Internal
fun createModuleSourcesNamesFilter(excludes: List<PathMatcher>): (String) -> Boolean {
  return { name ->
    val p = Path.of(name)
    excludes.none { it.matches(p) }
  }
}

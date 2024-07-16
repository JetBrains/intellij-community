package com.intellij.tools.launch.environments

import java.nio.file.Path

typealias PathInLaunchEnvironment = String

fun Path.resolve(
  baseLocalPath: Path,
  baseEnvPath: PathInLaunchEnvironment,
  envFileSeparator: Char
): PathInLaunchEnvironment {
  assert(startsWith(baseLocalPath)) { "$this doesn't start with $baseLocalPath" }
  val relativeLocalPath = baseLocalPath.relativize(this)
  val relativeEnvPath = relativeLocalPath.joinToString(separator = envFileSeparator.toString())
  return if (relativeEnvPath.isEmpty()) {
    baseEnvPath
  }
  else {
    baseEnvPath + envFileSeparator + relativeEnvPath
  }
}
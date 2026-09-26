// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

/**
 * How the `HOST_PLATFORMS` list of `build/dev_launch_dependencies.bzl` spells [os]: `darwin`, `linux`, or `windows`.
 * The one owner of that spelling on the Kotlin side.
 */
fun devDistHostPlatformOs(os: OsFamily): String = when (os) {
  OsFamily.MACOS -> "darwin"
  OsFamily.LINUX -> "linux"
  OsFamily.WINDOWS -> "windows"
}

/** How the same list spells [arch]: `aarch64` or `x64`. */
fun devDistHostPlatformArch(arch: JvmArchitecture): String = when (arch) {
  JvmArchitecture.aarch64 -> "aarch64"
  JvmArchitecture.x64 -> "x64"
}

/**
 * The `HOST_PLATFORMS` entry of a dev distribution for [os] and [arch], such as `darwin_aarch64`. A layout names it
 * in `DevPluginLayoutAsset.hostPlatforms` when an asset serves one host platform only.
 */
fun devDistHostPlatform(os: OsFamily, arch: JvmArchitecture): String = "${devDistHostPlatformOs(os)}_${devDistHostPlatformArch(arch)}"

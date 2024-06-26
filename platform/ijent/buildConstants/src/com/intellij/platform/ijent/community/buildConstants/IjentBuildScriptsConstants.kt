// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.buildConstants

/**
 * Decides if the IJent WSL filesystem should be enabled by default in some IDE.
 * Users are still able to explicitly disable the filesystem through the registry and/or VM options.
 *
 * [platformPrefix] corresponds to `-Didea.platform.prefix`,
 * `component>names>script` in `ApplicationInfo.xml`,
 * `com.intellij.testFramework.common.PlatformPrefix.PREFIX_CANDIDATES`.
 */
fun isIjentWslFsEnabledByDefaultForProduct(platformPrefix: String?): Boolean =
  platformPrefix in IJENT_ENABLED_BY_DEFAULT_IN

private val IJENT_ENABLED_BY_DEFAULT_IN: Collection<String> = listOf(
  "idea",
  "Idea",
  "WebStorm",
)

const val IJENT_BOOT_CLASSPATH_MODULE = "intellij.platform.core.nio.fs"

const val IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY = "wsl.use.remote.agent.for.nio.filesystem"

val ENABLE_IJENT_WSL_FILE_SYSTEM_VMOPTIONS: List<String> = listOf(
  "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
  "-Djava.security.manager=com.intellij.platform.core.nio.fs.CoreBootstrapSecurityManager",
  "-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=true"
)
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.buildConstants

/**
 * Decides if the IJent WSL filesystem should be enabled by default in some IDE.
 * Users are still able to explicitly disable the filesystem through the registry and/or VM options.
 *
 * [platformPrefix] corresponds to `-Didea.platform.prefix`,
 * `component>names>script` in `ApplicationInfo.xml`,
 * `com.intellij.testFramework.common.PlatformPrefix.PREFIX_CANDIDATES`.
 */
fun isIjentWslFsEnabledByDefaultForProduct(platformPrefix: String?): Boolean {
  return platformPrefix !in IJENT_DISABLED_BY_DEFAULT_IN
}

/**
 * In case of problems in a particular IDE and inability to fix them quickly, add the platform prefix here.
 * The platform prefix is defined in `org.jetbrains.intellij.build.ProductProperties.platformPrefix`.
 */
private val IJENT_DISABLED_BY_DEFAULT_IN: Collection<String> = listOf(
  "JetBrainsClient",
  "Gateway",
)

const val IJENT_BOOT_CLASSPATH_MODULE: String = "intellij.platform.core.nio.fs"

const val IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY: String = "wsl.use.remote.agent.for.nio.filesystem"

const val IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS: String = "com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider"

val MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS: List<String> = listOf(
  "-Djava.nio.file.spi.DefaultFileSystemProvider=$IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS",
)
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.ijent.community.buildConstants

/**
 * This option is used by DevKit reflectively.
 * Especially important, it's used by older versions of DevKit,
 * so even if you don't see its usages in the source code now, they can still exist in stable branches.
 * Unless you're modifying DevKit, please don't touch it.
 */
@Deprecated("Replace with isMultiRoutingFileSystemEnabledForProduct")
fun isIjentWslFsEnabledByDefaultForProduct(platformPrefix: String?): Boolean {
  return isMultiRoutingFileSystemEnabledForProduct(platformPrefix)
}

/**
 * Decides if the multi-routing filesystem should be enabled by default in some IDE.
 * Users are still able to explicitly disable the filesystem through the registry and/or VM options.
 *
 * See `com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider`.
 *
 * [platformPrefix] corresponds to `-Didea.platform.prefix`,
 * `component>names>script` in `ApplicationInfo.xml`,
 * `com.intellij.testFramework.common.PlatformPrefix.PREFIX_CANDIDATES`.
 */
fun isMultiRoutingFileSystemEnabledForProduct(platformPrefix: String?): Boolean {
  return platformPrefix == null || !MRFS_AND_IJENT_DISABLED_BY_DEFAULT_IN.contains(platformPrefix)
}

/**
 * In case of problems in a particular IDE and inability to fix them quickly, add the platform prefix here.
 * The platform prefix is defined in `org.jetbrains.intellij.build.ProductProperties.platformPrefix`.
 */
private val MRFS_AND_IJENT_DISABLED_BY_DEFAULT_IN: Collection<String> = java.util.List.of(
  "JetBrainsClient",
  "Gateway",
)

const val IJENT_BOOT_CLASSPATH_MODULE: String = "intellij.platform.core.nio.fs"

const val IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY: String = "wsl.use.remote.agent.for.nio.filesystem"

const val IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS: String = "com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider"

val MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS: List<String> = java.util.List.of(
  "-Djava.nio.file.spi.DefaultFileSystemProvider=$IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS",
)

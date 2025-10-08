// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.libraries

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModule

/**
 * A library module is supposed to contain no source code and depend on Maven dependencies or another library module.
 * It is used to transform libraries into consumable content modules (V2 plugin model).
 * Won't be published as a Maven artifact, see [org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder].
 */
@ApiStatus.Internal
fun JpsModule.isLibraryModule(): Boolean {
  return name in LIBRARY_MODULE_NAMES
}

/**
 * Please mind that intellij.libraries.microba and intellij.libraries.cglib are exceptions and should not be included in this list
 */
private val LIBRARY_MODULE_NAMES: Set<String> = setOf(
  "intellij.libraries.aalto.xml",
  "intellij.libraries.asm",
  "intellij.libraries.assertj.core",
  "intellij.libraries.caffeine",
  "intellij.libraries.compose.foundation.desktop",
  "intellij.libraries.compose.foundation.desktop.junit",
  "intellij.libraries.compose.runtime.desktop",
  "intellij.libraries.fastutil",
  "intellij.libraries.grpc",
  "intellij.libraries.grpc.netty.shaded",
  "intellij.libraries.gson",
  "intellij.libraries.hamcrest",
  "intellij.libraries.icu4j",
  "intellij.libraries.ion",
  "intellij.libraries.jackson",
  "intellij.libraries.jackson.databind",
  "intellij.libraries.jackson.jr.objects",
  "intellij.libraries.jackson.module.kotlin",
  "intellij.libraries.junit4",
  "intellij.libraries.junit5",
  "intellij.libraries.kotlinTest",
  "intellij.libraries.kotlinTestAssertionsCoreJvm",
  "intellij.libraries.kotlin.reflect",
  "intellij.libraries.kotlinx.coroutines.slf4j",
  "intellij.libraries.lz4",
  "intellij.libraries.ktor.client",
  "intellij.libraries.ktor.client.cio",
  "intellij.libraries.ktor.io",
  "intellij.libraries.ktor.network.tls",
  "intellij.libraries.ktor.utils",
  "intellij.libraries.kotlinx.io",
  "intellij.libraries.skiko",
  "intellij.libraries.coil",
  "intellij.libraries.miglayout.swing",
  "intellij.libraries.proxy.vole",
  "intellij.libraries.mvstore",
  "intellij.libraries.rhino",
  "intellij.libraries.testng",
  "intellij.libraries.winp",
  "intellij.libraries.xml.rpc",
  "intellij.libraries.xstream",
  "intellij.libraries.xz",
  "intellij.libraries.dokka",
  "intellij.libraries.guava",
  "intellij.libraries.hash4j",
  "intellij.libraries.xerces",
)

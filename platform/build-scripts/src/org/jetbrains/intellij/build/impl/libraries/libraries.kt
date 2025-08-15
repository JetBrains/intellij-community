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
  "intellij.libraries.assertj.core",
  "intellij.libraries.compose.foundation.desktop",
  "intellij.libraries.compose.foundation.desktop.junit",
  "intellij.libraries.grpc",
  "intellij.libraries.grpc.netty.shaded",
  "intellij.libraries.hamcrest",
  "intellij.libraries.junit4",
  "intellij.libraries.junit5",
  "intellij.libraries.kotlinTest",
  "intellij.libraries.kotlinTestAssertionsCoreJvm",
  "intellij.libraries.ktor.client",
  "intellij.libraries.ktor.client.cio",
  "intellij.libraries.kotlinx.io",
  "intellij.libraries.skiko",
  "intellij.libraries.coil",
  "intellij.libraries.testng",
  "intellij.libraries.dokka",
  "intellij.libraries.xerces",
)

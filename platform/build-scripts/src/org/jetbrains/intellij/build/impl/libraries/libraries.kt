// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.libraries

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

/**
 * A library module is supposed to contain no source code and depend on Maven dependencies or another library module.
 * It is used to transform libraries into consumable content modules (V2 plugin model).
 * Won't be published as a Maven artifact, see [org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder].
 */
@ApiStatus.Internal
fun JpsModule.isLibraryModule(): Boolean {
  return name.startsWith("intellij.libraries.") &&
         sourceRoots.none { it.rootType == JavaSourceRootType.SOURCE }
}

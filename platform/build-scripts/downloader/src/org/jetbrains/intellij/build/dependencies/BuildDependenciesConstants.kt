// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BuildDependenciesConstants {
  const val INTELLIJ_DEPENDENCIES_URL: String = "https://cache-redirector.jetbrains.com/intellij-dependencies"
  const val MAVEN_CENTRAL_URL: String = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
  const val JPS_AUTH_SPACE_USERNAME: String = "jps.auth.spaceUsername"
  const val JPS_AUTH_SPACE_PASSWORD: String = "jps.auth.spacePassword"
}

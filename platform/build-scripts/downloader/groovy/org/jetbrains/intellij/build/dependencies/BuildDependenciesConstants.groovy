// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import org.jetbrains.annotations.ApiStatus;

@CompileStatic
@ApiStatus.Internal
class BuildDependenciesConstants {
  public static final String INTELLIJ_DEPENDENCIES_URL = "https://cache-redirector.jetbrains.com/intellij-dependencies";
  public static final String MAVEN_CENTRAL_URL = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2";
}

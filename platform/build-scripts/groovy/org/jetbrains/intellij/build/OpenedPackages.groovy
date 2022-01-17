// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import groovy.transform.CompileStatic

import java.nio.file.Paths

@CompileStatic
final class OpenedPackages implements Iterable<String> {
  /** Opened packages need to be shared at least with [com.intellij.ide.starter.extended.runner.IdeFromCodeLauncher] */
  @SuppressWarnings('SpellCheckingInspection')
  private static final List<String> OPENED_PACKAGES =
    Paths.get(PathManager.getHomePathFor(OpenedPackages.class),
              "community", "platform", "build-scripts", "resources", "OpenedPackages.txt")
      .readLines()

  static final OpenedPackages INSTANCE = new OpenedPackages()

  private OpenedPackages() {}

  @Override
  Iterator<String> iterator() {
    return OPENED_PACKAGES.iterator()
  }
}

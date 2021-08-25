// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
final class ProjectLibraryData {
  String libraryName
  String relativeOutputPath
  /**
   * Do not merge library JAR file into uber JAR.
   */
  boolean standalone
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.Immutable

@Immutable
class ProjectLibraryData {
  String libraryName
  String relativeOutputPath
}

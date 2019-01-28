// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Describes library entry format in third-party libraries JSON file.
 * This file is used by external tools, so class properties shouldn't be changed.
 */
@Immutable
@CompileStatic
class LibraryLicenseData {
  String name
  String url
  String version
  String license
  String licenseUrl
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import groovy.transform.CompileStatic

@CompileStatic
class CompilationOutput {
  final String hash
  final String path
  final String sourcePath

  CompilationOutput(String name, String type, String hash, String path) {
    this.hash = hash
    this.path = path
    this.sourcePath = "$type/$name/$hash"
  }
}

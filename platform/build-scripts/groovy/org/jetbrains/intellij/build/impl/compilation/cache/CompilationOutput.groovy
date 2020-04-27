// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import groovy.transform.CompileStatic

@CompileStatic
class CompilationOutput {
  final String name
  final String type
  final String hash
  final String path

  CompilationOutput(String name, String type, String hash, String path) {
    this.name = name
    this.type = type
    this.hash = hash
    this.path = path
  }
}

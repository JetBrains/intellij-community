// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import groovy.transform.CompileStatic

@CompileStatic
class BuildTargetState {
  final String hash
  final String relativePath

  private BuildTargetState(String hash, String relativePath) {
    this.hash = hash
    this.relativePath = relativePath
  }
}
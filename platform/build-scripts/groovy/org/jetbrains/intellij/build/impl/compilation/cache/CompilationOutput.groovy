// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import groovy.transform.CompileStatic

/**
 * Compiled bytecode of project module, cannot be used for incremental compilation without {@link org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache.JpsCaches}
 */
@CompileStatic
final class CompilationOutput {
  final String hash
  final String path
  final String remotePath

  CompilationOutput(String name, String type, String hash, String path) {
    this.hash = hash
    this.path = path
    this.remotePath = "$type/$name/$hash"
  }
}

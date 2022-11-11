// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import java.nio.file.Path

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to sign the product's files.
 */
interface SignTool {
  fun signFiles(files: List<Path>, context: BuildContext, options: Map<String, String>)
  fun commandLineClient(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path?
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to sign the product's files.
 */
@CompileStatic
interface SignTool {
  /**
   * @param path absolute path (with '/' as a separator) to file to be signed
   */
  void signFile(String path, BuildContext context, Map<String, String> options)
}
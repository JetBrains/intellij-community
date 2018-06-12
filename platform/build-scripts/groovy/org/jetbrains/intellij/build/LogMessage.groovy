// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
/**
 * @author nik
 */
@CompileStatic
class LogMessage {
  enum Kind {
    ERROR, WARNING, DEBUG, INFO, PROGRESS, BLOCK_STARTED, BLOCK_FINISHED, ARTIFACT_BUILT, @Deprecated COMPILATION_ERROR, COMPILATION_ERRORS,
    STATISTICS, BUILD_STATUS, SET_PARAMETER
  }
  final Kind kind
  final String text

  LogMessage(Kind kind, String text) {
    this.kind = kind
    this.text = text
  }
}
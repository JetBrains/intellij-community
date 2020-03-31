// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.LogMessage

@CompileStatic
class CompilationErrorsLogMessage extends LogMessage {
  final String compilerName
  final List<String> errorMessages

  CompilationErrorsLogMessage(String compilerName, List<String> errorMessages) {
    super(Kind.COMPILATION_ERRORS, "$compilerName compilation errors")
    this.errorMessages = errorMessages
    this.compilerName = compilerName
  }
}

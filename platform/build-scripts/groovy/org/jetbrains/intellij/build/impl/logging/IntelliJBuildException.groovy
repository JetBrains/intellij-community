// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.logging

import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException

@CompileStatic
class IntelliJBuildException extends BuildException {
  IntelliJBuildException(String location, String message, Throwable cause) {
    super(location == null || location.isEmpty() ? message : location + ': ' + message, cause)
  }
}

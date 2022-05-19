// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging;

import org.apache.tools.ant.BuildException;

public class IntelliJBuildException extends BuildException {
  public IntelliJBuildException(String location, String message, Throwable cause) {
    super(location == null || location.isEmpty() ? message : location + ": " + message, cause);
  }
}

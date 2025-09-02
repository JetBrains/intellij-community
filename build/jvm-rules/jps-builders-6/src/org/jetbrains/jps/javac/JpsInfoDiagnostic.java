// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.Nls;

public final class JpsInfoDiagnostic extends PlainMessageDiagnostic{
  public JpsInfoDiagnostic(@Nls String message) {
    super(Kind.OTHER, message);
  }
}

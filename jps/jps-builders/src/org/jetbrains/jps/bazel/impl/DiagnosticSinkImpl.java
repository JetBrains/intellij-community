// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.Message;

public abstract class DiagnosticSinkImpl implements DiagnosticSink {
  protected boolean myHasErrors;

  @Override
  public void report(Message msg) {
    if (msg.getKind() == Message.Kind.ERROR) {
      myHasErrors = true;
    }
  }

  @Override
  public final boolean hasErrors() {
    return myHasErrors;
  }
}

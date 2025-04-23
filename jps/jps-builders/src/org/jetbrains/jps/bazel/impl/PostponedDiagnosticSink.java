// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.Message;

import java.util.ArrayList;
import java.util.List;

public final class PostponedDiagnosticSink extends DiagnosticSinkImpl {
  private final List<Message> myMessages = new ArrayList<>();

  @Override
  public void report(Message msg) {
    super.report(msg);
    myMessages.add(msg);
  }

  public void drainTo(DiagnosticSink sink) {
    for (Message message : myMessages) {
      sink.report(message);
    }
    myMessages.clear();
    myHasErrors = false;
  }
}

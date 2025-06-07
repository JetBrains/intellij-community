// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.DiagnosticSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.Message;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.jps.util.Iterators.find;

public final class PostponedDiagnosticSink implements DiagnosticSink {
  private final List<Message> myMessages = new ArrayList<>();

  @Override
  public void report(Message msg) {
    myMessages.add(msg);
  }

  @Override
  public boolean hasErrors() {
    return find(myMessages, msg -> msg.getKind() == Message.Kind.ERROR) != null;
  }

  public void drainTo(DiagnosticSink sink) {
    for (Message message : myMessages) {
      sink.report(message);
    }
    myMessages.clear();
  }
}

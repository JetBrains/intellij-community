// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.DiagnosticSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.Message;
import org.jetbrains.jps.util.Iterators;

import java.util.ArrayList;
import java.util.List;

public final class PostponedDiagnosticSink implements DiagnosticSink {
  private final List<Message> myMessages = new ArrayList<>();

  @Override
  public void report(Message msg) {
    myMessages.add(msg);
  }

  @Override
  public boolean hasErrors() {
    return !Iterators.isEmpty(getErrors());
  }

  @Override
  public Iterable<Message> getErrors() {
    return Iterators.filter(myMessages, msg -> msg.getKind() == Message.Kind.ERROR);
  }

  public void drainTo(DiagnosticSink sink) {
    for (Message message : myMessages) {
      sink.report(message);
    }
    myMessages.clear();
  }
}

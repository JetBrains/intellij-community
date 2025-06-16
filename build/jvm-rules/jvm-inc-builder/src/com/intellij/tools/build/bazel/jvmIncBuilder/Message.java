// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.runner.Runner;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

public interface Message {
  enum Kind {
    ERROR, WARNING, INFO, STDOUT
  }

  Kind getKind();

  String getText();

  Runner getSource();

  //-----------------------------------------------------------

  static Message error(Runner reporter, String text) {
    return create(reporter, Kind.ERROR, text);
  }

  static Message warning(Runner reporter, String text) {
    return create(reporter, Kind.WARNING, text);
  }

  static Message info(Runner reporter, String text) {
    return create(reporter, Kind.INFO, text);
  }

  static Message stdOut(Runner reporter, String text) {
    return create(reporter, Kind.STDOUT, text);
  }

  static Message create(Runner reporter, Kind messageKind, String text) {
    return create(reporter, messageKind, text, (String)null);
  }

  static Message create(Runner reporter, Kind messageKind, String text, @Nullable String srcPath) {
    String message = srcPath == null? text : text + "\n\t" + srcPath;
    return new Message() {
      @Override
      public Kind getKind() {
        return messageKind;
      }

      @Override
      public String getText() {
        return message;
      }

      @Override
      public Runner getSource() {
        return reporter;
      }
    };
  }

  static Message create(Runner reporter, Throwable ex) {
    return create(reporter, Kind.ERROR, ex);
  }
  
  static Message create(Runner reporter, Kind kind, Throwable ex) {
    return create(reporter, kind, ex.getMessage(), ex);
  }

  static Message create(Runner reporter, Kind kind, String message, Throwable ex) {
    StringBuilder buf = new StringBuilder(message != null? message : ex.getMessage());
    if (kind == Kind.ERROR) {
      StringWriter trace = new StringWriter();
      ex.printStackTrace(new PrintWriter(trace));
      buf.append("\n").append(trace);
    }
    String text = buf.toString();
    return new Message() {
      @Override
      public Kind getKind() {
        return kind;
      }

      @Override
      public String getText() {
        return text;
      }

      @Override
      public Runner getSource() {
        return reporter;
      }
    };
  }
}

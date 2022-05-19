// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

public class LogMessage {
  public LogMessage(Kind kind, String text) {
    this.kind = kind;
    this.text = text;
  }

  public final Kind getKind() {
    return kind;
  }

  public final String getText() {
    return text;
  }

  private final Kind kind;
  private final String text;

  public static enum Kind {
    ERROR, WARNING, DEBUG, INFO, PROGRESS, BLOCK_STARTED, BLOCK_FINISHED, ARTIFACT_BUILT, COMPILATION_ERRORS, STATISTICS, BUILD_STATUS, SET_PARAMETER;
  }
}

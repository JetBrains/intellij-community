// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import org.jetbrains.annotations.NotNull;

public class ProjectStructureProblemType {
  public enum Severity { ERROR, WARNING, UNUSED }

  private final String myId;
  private final Severity mySeverity;

  public ProjectStructureProblemType(@NotNull String id, @NotNull Severity severity) {
    myId = id;
    mySeverity = severity;
  }

  public static ProjectStructureProblemType error(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.ERROR);
  }

  public static ProjectStructureProblemType warning(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.WARNING);
  }

  public static ProjectStructureProblemType unused(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.UNUSED);
  }

  public @NotNull String getId() {
    return myId;
  }

  public @NotNull Severity getSeverity() {
    return mySeverity;
  }
}

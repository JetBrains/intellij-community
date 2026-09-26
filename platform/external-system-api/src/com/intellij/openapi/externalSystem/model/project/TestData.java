// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Set;

public final class TestData extends AbstractExternalEntityData {
  private final @NotNull String testName;
  private final @NotNull String testTaskName;
  private final @NotNull Set<String> sourceFolders;

  @PropertyMapping({"owner", "testName", "testTaskName", "sourceFolders"})
  public TestData(
    @NotNull ProjectSystemId owner,
    @NotNull String testName,
    @NotNull String testTaskName,
    @NotNull Set<String> sourceFolders
  ) {
    super(owner);
    this.testName = testName;
    this.testTaskName = testTaskName;
    this.sourceFolders = sourceFolders;
  }

  public @NotNull String getTestName() {
    return testName;
  }

  public @NotNull String getTestTaskName() {
    return testTaskName;
  }

  public @NotNull @Unmodifiable Set<String> getSourceFolders() {
    return Collections.unmodifiableSet(sourceFolders);
  }
}

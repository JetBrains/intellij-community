// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public final class TestData extends AbstractExternalEntityData {
  private final @NotNull String testName;
  private final @NotNull String testTaskName;
  private final @NotNull String cleanTestTaskName;
  private final @NotNull Set<String> sourceFolders;

  @PropertyMapping({"owner", "testName", "testTaskName", "cleanTestTaskName", "sourceFolders"})
  public TestData(
    @NotNull ProjectSystemId owner,
    @NotNull String testName,
    @NotNull String testTaskName,
    @NotNull String cleanTestTaskName,
    @NotNull Set<String> sourceFolders
  ) {
    super(owner);
    this.testName = testName;
    this.testTaskName = testTaskName;
    this.cleanTestTaskName = cleanTestTaskName;
    this.sourceFolders = sourceFolders;
  }

  @NotNull
  public String getTestName() {
    return testName;
  }

  @NotNull
  public String getTestTaskName() {
    return testTaskName;
  }

  @NotNull
  public String getCleanTestTaskName() {
    return cleanTestTaskName;
  }

  @NotNull
  public Set<String> getSourceFolders() {
    return Collections.unmodifiableSet(sourceFolders);
  }
}

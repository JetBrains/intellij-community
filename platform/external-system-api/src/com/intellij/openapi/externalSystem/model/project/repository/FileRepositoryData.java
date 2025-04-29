// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.repository;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class FileRepositoryData extends ProjectRepositoryData {

  private final @NotNull List<String> files;

  @PropertyMapping({"owner", "name", "files"})
  public FileRepositoryData(@NotNull ProjectSystemId owner, @NotNull String name, @NotNull List<String> files) {
    super(owner, name);
    this.files = files;
  }

  public @NotNull List<String> getFiles() {
    return files;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    FileRepositoryData data = (FileRepositoryData)o;
    return Objects.equals(files, data.files);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), files);
  }
}

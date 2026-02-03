// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.repository;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class UrlRepositoryData extends ProjectRepositoryData {

  public enum Type {
    MAVEN,
    IVY,
    OTHER
  }

  private final @Nullable String url;
  private final @NotNull Type type;

  @PropertyMapping({"owner", "name", "url", "type"})
  public UrlRepositoryData(@NotNull ProjectSystemId owner,
                           @NotNull String name,
                           @Nullable String url,
                           @NotNull Type type) {
    super(owner, name);
    this.url = url;
    this.type = type;
  }

  public @Nullable String getUrl() {
    return url;
  }

  public @NotNull Type getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    UrlRepositoryData data = (UrlRepositoryData)o;
    return Objects.equals(url, data.url) && type == data.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), url, type);
  }
}

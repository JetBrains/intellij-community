// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * The general idea of 'external system' integration is to provide management facilities for the project structure defined in
 * terms over than IntelliJ (e.g. maven, gradle, eclipse etc).
 * <p/>
 * This class serves as an id of a system which defines project structure, i.e. it might be any external system or the ide itself.
 */
public final class ProjectSystemId implements Serializable {
  private static final long serialVersionUID = 2L;
  private static final Map<String, ProjectSystemId> ourExistingIds = new ConcurrentHashMap<>();

  public static final @NotNull ProjectSystemId IDE = new ProjectSystemId("IDE");

  private final @NotNull @NonNls String id;
  private final @NotNull @Nls(capitalization = Title) String readableName;

  public ProjectSystemId(@NotNull @NlsSafe String id) {
    this(id, StringUtil.capitalize(StringUtil.toLowerCase(id)));
  }

  @PropertyMapping({"id", "readableName"})
  public ProjectSystemId(@NotNull @NonNls String id, @NotNull @Nls(capitalization = Title) String readableName) {
    this.id = id;
    this.readableName = readableName;
    ourExistingIds.putIfAbsent(id, this);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectSystemId owner = (ProjectSystemId)o;

    return id.equals(owner.id);
  }

  public @NotNull String getId() {
    return id;
  }

  public @NotNull @Nls(capitalization = Title) String getReadableName() {
    return readableName;
  }

  @Override
  public String toString() {
    return id;
  }

  public @NotNull ProjectSystemId intern() {
    ProjectSystemId current = ourExistingIds.putIfAbsent(this.id, this);
    return current == null ? this : current;
  }

  public static @Nullable ProjectSystemId findById(@NotNull String id) {
    return ourExistingIds.get(id);
  }

  private Object readResolve() {
    ProjectSystemId cached = ourExistingIds.get(id);
    if (cached != null) {
      return cached;
    }
    else {
      return this;
    }
  }
}

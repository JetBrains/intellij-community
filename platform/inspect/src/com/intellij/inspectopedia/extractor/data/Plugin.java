// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class Plugin {
  public String id;

  public String name;

  public String version;

  public List<Inspection> inspections;

  public Plugin(String id, String name, String version) {
    this.id = id;
    this.name = name;
    this.version = version;
    this.inspections = new ArrayList<>();
  }

  public Plugin() {
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  @NotNull
  public List<Inspection> getInspections() {
    return Optional.ofNullable(inspections)
      .map((Function<List<Inspection>, List<Inspection>>)ArrayList::new)
      .orElse(Collections.emptyList());
  }

  public void addInspection(@NotNull Inspection inspection) {
    inspections.add(inspection);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Plugin plugin = (Plugin)o;
    return Objects.equals(id, plugin.id) && Objects.equals(name, plugin.name) && Objects.equals(version, plugin.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, version);
  }
}

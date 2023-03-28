// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Service(value = Service.Level.PROJECT)
@ApiStatus.Internal
public final class InspectionUsageStorage {
  public static InspectionUsageStorage getInstance(Project project) {
    return project.getService(InspectionUsageStorage.class);
  }
  private Set<String> inspectionsReportingProblems = newStorage();

  @NotNull
  private static Set<@NotNull String> newStorage() {
    return ContainerUtil.newConcurrentSet();
  }

  public void reportInspectionsWhichReportedProblems(Set<String> inspectionIds) {
    inspectionsReportingProblems.addAll(inspectionIds);
  }

  @NotNull
  public Report collectHighligtingReport() {
    Set<String> old = inspectionsReportingProblems;
    inspectionsReportingProblems = newStorage();
    return new Report(old);
  }

  public record Report(Set<String> inspectionsReportingProblems) {}
}
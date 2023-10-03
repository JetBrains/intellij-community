// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the set of inspections that produced some diagnostics in the current highlighting session, to report them to FUS.
 * After the statistics is reported, the storage is reset
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public final class InspectionUsageFUSStorage {
  public static InspectionUsageFUSStorage getInstance(Project project) {
    return project.getService(InspectionUsageFUSStorage.class);
  }
  private Set<String> inspectionsReportingProblems = newStorage();
  private final AtomicInteger inspectionSessions = new AtomicInteger(0);

  private static @NotNull Set<@NotNull String> newStorage() {
    return ContainerUtil.newConcurrentSet();
  }

  public void reportInspectionsWhichReportedProblems(@NotNull Set<String> inspectionIds) {
    inspectionsReportingProblems.addAll(inspectionIds);
    inspectionSessions.incrementAndGet();
  }

  public @NotNull Report collectHighligtingReport() {
    Set<String> old = inspectionsReportingProblems;
    inspectionsReportingProblems = newStorage();
    int inspectionSessionCount = inspectionSessions.getAndSet(0);
    return new Report(old, inspectionSessionCount);
  }

  public record Report(@NotNull Set<@NotNull String> inspectionsReportingProblems, int inspectionSessionCount) {}
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ClassicFileStatusMapState implements FileStatusMapState {
  private final @NotNull Project myProject;
  private final @NotNull Map<Document, FileStatus> myDocumentToStatusMap = new WeakHashMap<>(); // all dirty if absent

  ClassicFileStatusMapState(@NotNull Project project) {
    this.myProject = project;
  }

  @Override
  public @NotNull FileStatus getOrCreateStatus(@NotNull Document document, @NotNull CodeInsightContext context) {
    return myDocumentToStatusMap.computeIfAbsent(document, __ -> new FileStatus(myProject));
  }

  @Override
  public @Nullable FileStatus getStatusOrNull(@NotNull Document document, @NotNull CodeInsightContext context) {
    return myDocumentToStatusMap.get(document);
  }

  @Override
  public boolean isEmpty() {
    return myDocumentToStatusMap.isEmpty();
  }

  @Override
  public void clear() {
    myDocumentToStatusMap.clear();
  }

  @Override
  public @NotNull String toString(@NotNull Document document) {
    return String.valueOf(myDocumentToStatusMap.get(document));
  }

  @Override
  public @NotNull List<FileStatus> getFileStatuses(@NotNull Document document) {
    FileStatus status = myDocumentToStatusMap.get(document);
    return status != null ? List.of(status) : Collections.emptyList();
  }
}

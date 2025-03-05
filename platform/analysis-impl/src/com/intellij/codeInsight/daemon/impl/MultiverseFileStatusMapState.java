// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextKt;
import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class MultiverseFileStatusMapState implements FileStatusMapState {
  private final @NotNull Project myProject;
  private final @NotNull Map<Document, Map<CodeInsightContext, FileStatus>> myDocumentToStatusMap = new WeakHashMap<>();

  MultiverseFileStatusMapState(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull FileStatus getOrCreateStatus(@NotNull Document document, @NotNull CodeInsightContext context) {
    CodeInsightContext effectiveContext = resolveAnyContext(document, context);
    Map<CodeInsightContext, FileStatus> statusMap = myDocumentToStatusMap.computeIfAbsent(document, __ -> new WeakHashMap<>());
    return statusMap.computeIfAbsent(effectiveContext, __ -> new FileStatus(myProject));
  }

  @Override
  public @Nullable FileStatus getStatusOrNull(@NotNull Document document, @NotNull CodeInsightContext context) {
    Map<CodeInsightContext, FileStatus> statusMap = myDocumentToStatusMap.get(document);
    if (statusMap == null) return null;

    CodeInsightContext effectiveContext = resolveAnyContext(document, context);
    return statusMap.get(effectiveContext);
  }

  @Override
  public boolean isEmpty() {
    // todo ijpl-339 is it correct???
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
  public @NotNull Collection<FileStatus> getFileStatuses(@NotNull Document document) {
    Map<CodeInsightContext, FileStatus> statusMap = myDocumentToStatusMap.get(document);
    return statusMap != null ? statusMap.values() : Collections.emptyList();
  }

  private @NotNull CodeInsightContext resolveAnyContext(@NotNull Document document, @NotNull CodeInsightContext context) {
    if (context != CodeInsightContextKt.anyContext()) {
      return context;
    }

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) {
      return CodeInsightContextKt.defaultContext();
    }

    return CodeInsightContextManager.getInstance(myProject).getPreferredContext(file);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.patches;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class PatchCreator {
  public static void create(@NotNull Project project, @NotNull List<? extends Change> changes, @NotNull Path file, boolean isReverse, @Nullable CommitContext commitContext)
    throws IOException, VcsException {
    Path basePath = ProjectKt.getStateStore(project).getProjectBasePath();
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, changes, basePath, isReverse, false);
    PatchWriter.writePatches(project, file, basePath, patches, commitContext);
  }
}

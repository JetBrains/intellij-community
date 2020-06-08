// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.patches;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

public final class PatchCreator {
  public static void create(Project p, List<? extends Change> changes, String filePath, boolean isReverse, CommitContext commitContext)
    throws IOException, VcsException {
    String basePath = Objects.requireNonNull(p.getBasePath());
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(p, changes, basePath, isReverse);
    PatchWriter.writePatches(p, filePath, basePath, patches, commitContext, Charset.defaultCharset());
  }
}

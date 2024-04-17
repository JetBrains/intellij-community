// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui;

import com.intellij.history.integration.PatchingTestCase;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectoryHistoryDialogPatchCreationTest extends PatchingTestCase {
  public void testPatchCreation() throws Exception {
    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(myProject, myGateway, getVcs(), myRoot);
    m.clearRevisions();
    createChildDataWithoutContent(myRoot, "f0.txt");
    createChildDataWithContent(myRoot, "f1.txt");
    createChildDataWithContent(myRoot, "f2.txt");
    createChildDataWithContent(myRoot, "f3.txt");
    assertSize(8, m.getRevisions());

    m.selectRevisions(1, 7);
    Path basePath = myRoot.toNioPath();
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, m.getChanges(), basePath, false, false);
    PatchWriter.writePatches(myProject, patchFilePath, basePath, patches, null);
    clearRoot();

    applyPatch();

    assertThat(basePath.resolve("f0.txt")).isRegularFile();
    assertThat(basePath.resolve("f1.txt")).isRegularFile();
    assertThat(basePath.resolve("f2.txt")).isRegularFile();
    assertThat(basePath.resolve("f3.txt")).doesNotExist();
  }
}
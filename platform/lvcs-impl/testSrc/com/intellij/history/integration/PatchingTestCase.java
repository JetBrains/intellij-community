/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.integration;

import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class PatchingTestCase extends IntegrationTestCase {
  protected String patchFilePath;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    File dir = createTempDirectory();
    patchFilePath = new File(dir, "patch").getPath();
  }

  protected void clearRoot() throws IOException {
    for (VirtualFile f : myRoot.getChildren()) {
      f.delete(null);
    }
  }

  protected void applyPatch() throws Exception {
    PatchReader reader = PatchVirtualFileReader.create(LocalFileSystem.getInstance().refreshAndFindFileByPath(patchFilePath));

    List<FilePatch> patches = ObjectsConvertor.<FilePatch, TextFilePatch>downcast(reader.readAllPatches());

    new PatchApplier<BinaryFilePatch>(myProject, myRoot, patches, (LocalChangeList) null, null, null).execute();
  }
}

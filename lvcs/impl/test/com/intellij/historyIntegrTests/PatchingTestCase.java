package com.intellij.historyIntegrTests;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public abstract class PatchingTestCase extends IntegrationTestCase {
  protected String patchFilePath;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    File dir = createTempDirectory();
    patchFilePath = new File(dir, "patch").getPath();
  }

  protected void clearRoot() throws IOException {
    for (VirtualFile f : root.getChildren()) {
      f.delete(null);
    }
  }

  protected void applyPatch() throws Exception {
    List<FilePatch> patches = new ArrayList<FilePatch>();
    PatchReader reader = new PatchReader(getFS().refreshAndFindFileByPath(patchFilePath));

    while (true) {
      FilePatch p = reader.readNextPatch();
      if (p == null) break;
      patches.add(p);
    }

    ApplyPatchContext ctx = new ApplyPatchContext(root, 0, true, false);
    List<FilePath> files = new ArrayList<FilePath>();
    ApplyPatchAction.applyFilePatches(myProject, patches, ctx, files);
  }
}

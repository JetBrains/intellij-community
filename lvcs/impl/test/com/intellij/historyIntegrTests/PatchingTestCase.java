package com.intellij.historyIntegrTests;

import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    ApplyPatchAction.applyFilePatches(myProject, patches, ctx);
  }
}

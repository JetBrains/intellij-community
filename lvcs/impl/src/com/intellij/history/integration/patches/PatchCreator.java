package com.intellij.history.integration.patches;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchBuilder;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

public class PatchCreator {
  public static void create(IdeaGateway gw, List<Change> changes, String filePath, boolean isReverse)
    throws IOException, VcsException {
    Writer writer = new OutputStreamWriter(new FileOutputStream(filePath));
    try {
      List<FilePatch> patches = PatchBuilder.buildPatch(changes, gw.getBaseDir(), false, isReverse);
      final String lineSeparator = CodeStyleSettingsManager.getInstance(gw.getProject()).getCurrentSettings().getLineSeparator();
      UnifiedDiffWriter.write(patches, writer, lineSeparator);
    }
    finally {
      writer.close();
    }
  }
}

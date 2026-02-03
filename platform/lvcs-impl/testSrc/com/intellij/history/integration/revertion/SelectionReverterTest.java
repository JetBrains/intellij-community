// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.revertion;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.RevisionId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SelectionReverterTest extends IntegrationTestCase {
  private static final String COMMAND_NAME = "Revert command";
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = createChildData(myRoot, "f.txt");
  }

  public void testBasics() throws Exception {
    String before = """
      public class Bar {
        public String foo() {
          return "old";
        }
      }
      """;
    String after = """
      public class NewBar {
        public String foo() {
          return "new";
        }
        public abstract bar();
      }
      """;

    setBinaryContent(f, before.getBytes(StandardCharsets.UTF_8));
    setBinaryContent(f, after.getBytes(StandardCharsets.UTF_8));

    revertToPreviousRevision(2, 2);
    
    String expected = """
      public class NewBar {
        public String foo() {
          return "old";
        }
        public abstract bar();
      }
      """;
    assertEquals(expected, new String(f.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testChangeSetName() throws Exception {
    setBinaryContent(f, "one".getBytes(StandardCharsets.UTF_8));
    setBinaryContent(f, "two".getBytes(StandardCharsets.UTF_8));

    revertToPreviousRevision(0, 0);

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(4, changes.size());
    assertEquals(COMMAND_NAME, changes.get(0).getName());
  }

  public void testAskingForReadOnlyStatusClearingOnlyForTheSpecifiedFile() throws Exception {
    createChildData(myRoot, "foo1.txt");
    setBinaryContent(f, "one".getBytes(StandardCharsets.UTF_8));
    createChildData(myRoot, "foo2.txt");
    setBinaryContent(f, "two".getBytes(StandardCharsets.UTF_8));
    createChildData(myRoot, "foo3.txt");

    final List<VirtualFile> files = new ArrayList<>();
    myGateway = new IdeaGateway() {
      @Override
      public boolean ensureFilesAreWritable(@NotNull Project p, @NotNull List<? extends VirtualFile> ff) {
        files.addAll(ff);
        return true;
      }
    };

    List<String> errors = checkCanRevertToPreviousRevision();
    assertTrue(errors.isEmpty());

    assertEquals(1, files.size());
    assertEquals(f, files.get(0));
  }

  private void revertToPreviousRevision(int from, int to) throws Exception {
    createReverter(from, to).revert();
  }

  private List<String> checkCanRevertToPreviousRevision() throws IOException {
    return createReverter(0, 0).checkCanRevert();
  }

  private SelectionReverter createReverter(int from, int to) {
    List<RevisionId> revisions = getRevisionIdsFor(f);
    String entryPath = myGateway.getPathOrUrl(f);
    SelectionCalculator c = SelectionCalculator.create(getVcs(), myGateway, getRootEntry(), entryPath, revisions, from, to);
    return new SelectionReverter(myProject, getVcs(), myGateway, c, revisions.get(1), entryPath, from, to, () -> COMMAND_NAME);
  }
}

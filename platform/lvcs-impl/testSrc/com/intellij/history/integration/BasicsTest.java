// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicsTest extends IntegrationTestCase {
  public void testProcessingCommands() {
    final VirtualFile[] f = new VirtualFile[1];

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f[0] = createChildData(myRoot, "f1.txt");
        f[0].setBinaryContent(new byte[]{1});
        f[0].setBinaryContent(new byte[]{2});
      }
    }, "name", null));

    assertThat(getChangesFor(f[0])).hasSize(1);
  }

  public void testPuttingUserLabel() {
    VirtualFile f = createChildData(myRoot, "f.txt");

    LocalHistory.getInstance().putUserLabel(myProject, "global");

    assertEquals(2, getChangesFor(f).size());
    assertThat(getChangesFor(myRoot)).hasSize(3);

    LocalHistory.getInstance().putUserLabel(myProject, "file");

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(3, changes.size());
    assertEquals("file", changes.get(0).getLabel());
    assertEquals(-1, changes.get(0).getLabelColor());
    assertEquals("global", changes.get(1).getLabel());
    assertEquals(-1, changes.get(1).getLabelColor());
  }

  public void testPuttingSystemLabel() {
    VirtualFile f = createChildData(myRoot, "file.txt");

    assertEquals(1, getChangesFor(f).size());
    assertEquals(2, getChangesFor(myRoot).size());

    LocalHistory.getInstance().putSystemLabel(myProject, "label");

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(2, changes.size());
    assertEquals("label", changes.get(0).getLabel());

    changes = getChangesFor(myRoot);
    assertEquals(3, changes.size());
    assertEquals("label", changes.get(0).getLabel());
  }

  public void testPuttingLabelWithUnsavedDocuments() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setContent(f, "1");

    setDocumentTextFor(f, "2");
    LocalHistory.getInstance().putSystemLabel(myProject, "label");

    setDocumentTextFor(f, "3");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    setDocumentTextFor(f, "4");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    List<ChangeSet> rr = getChangesFor(f);
    assertEquals(8, rr.size()); // 5 changes + 3 labels
    assertEquals("4", getContentAsString(getCurrentEntry(f)));
    assertEquals("4", getContentAsString(getEntryFor(rr.get(0), f)));
    assertEquals("3", getContentAsString(getEntryFor(rr.get(1), f)));
    assertEquals("3", getContentAsString(getEntryFor(rr.get(2), f)));
    assertEquals("2", getContentAsString(getEntryFor(rr.get(3), f)));
    assertEquals("2", getContentAsString(getEntryFor(rr.get(4), f)));
    assertEquals("1", getContentAsString(getEntryFor(rr.get(5), f)));
    assertEquals("", getContentAsString(getEntryFor(rr.get(6), f)));
  }

  public void testDoNotRegisterSameUnsavedDocumentContentTwice() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setContent(f, "1");

    setDocumentTextFor(f, "2");
    LocalHistory.getInstance().putSystemLabel(myProject, "label");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    List<ChangeSet> rr = getChangesFor(f);
    assertEquals(5, rr.size()); // 3 changes + 2 labels
    assertEquals("2", getContentAsString(getCurrentEntry(f)));
    assertEquals("2", getContentAsString(getEntryFor(rr.get(0), f)));
    assertEquals("2", getContentAsString(getEntryFor(rr.get(1), f)));
    assertEquals("1", getContentAsString(getEntryFor(rr.get(2), f)));
    assertEquals("", getContentAsString(getEntryFor(rr.get(3), f)));
  }

  public void testIsUnderControl() {
    VirtualFile f1 = createChildData(myRoot, "file.txt");
    VirtualFile f2 = createChildData(myRoot, "file." + FileListeningTest.IGNORED_EXTENSION);

    assertTrue(LocalHistory.getInstance().isUnderControl(f1));
    assertFalse(LocalHistory.getInstance().isUnderControl(f2));
  }

  public void testDoNotRegisterChangesNotInLocalFS() throws Exception {
    File f = new File(myRoot.getPath(), "f.jar");
    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(f))) {

        jar.putNextEntry(new JarEntry("file.txt"));
        jar.write(1);
        jar.closeEntry();
      }
      return null;
    });

    VirtualFile vfile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
    assertNotNull(vfile);

    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assertEquals(1, jarRoot.findChild("file.txt").contentsToByteArray()[0]);

    assertThat(getChangesFor(myRoot)).hasSize(2);

    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(f))) {
        JarEntry e = new JarEntry("file.txt");
        e.setTime(f.lastModified() + 10000);
        jar.putNextEntry(e);
        jar.write(2);
        jar.closeEntry();
      }
      f.setLastModified(f.lastModified() + 10000);
      return null;
    });


    LocalFileSystem.getInstance().refreshWithoutFileWatcher(false);
    JarFileSystem.getInstance().refreshWithoutFileWatcher(false);
    jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assertThat((int)jarRoot.findChild("file.txt").contentsToByteArray()[0]).isEqualTo(2);

    assertThat(getChangesFor(myRoot)).hasSize(2);
    assertThat(getChangesFor(jarRoot)).hasSize(2);
  }
}

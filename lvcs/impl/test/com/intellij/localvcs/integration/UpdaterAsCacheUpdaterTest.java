package com.intellij.localvcs.integration;

import com.intellij.ide.startup.FileContent;
import com.intellij.localvcs.core.InMemoryLocalVcs;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

public class UpdaterAsCacheUpdaterTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  Updater updater;

  TestVirtualFile root;
  TestVirtualFile file;

  @Before
  public void setUp() {
    root = new TestVirtualFile("root");
    file = new TestVirtualFile("file", "new content", 1L);
    root.addChild(file);

    TestIdeaGateway gw = new TestIdeaGateway();
    gw.setContentRoots(root);
    updater = new Updater(vcs, gw);
  }

  @Test
  public void testCreatingNewFiles() {
    VirtualFile[] files = updater.queryNeededFiles();
    assertEquals(1, files.length);
    assertSame(file, files[0]);

    updater.processFile(fileContentOf(file));
    updater.updatingDone();

    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testUpdaingOutdatedFiles() {
    vcs.createDirectory("root");
    vcs.createFile("root/file", cf("old content"), file.getTimeStamp() - 1);

    VirtualFile[] files = updater.queryNeededFiles();
    assertEquals(1, files.length);
    assertSame(file, files[0]);

    updater.processFile(fileContentOf(file));
    updater.updatingDone();
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testCreatingNewFilesOnlyOnProcessingFile() {
    updater.queryNeededFiles();
    assertFalse(vcs.hasEntry("root/file"));

    updater.processFile(fileContentOf(file));
    assertTrue(vcs.hasEntry("root/file"));
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testUpdaingOutdatedFilesOnlyOnProcessingFile() {
    vcs.createDirectory("root");
    vcs.createFile("root/file", cf("old content"), file.getTimeStamp() - 1);

    updater.queryNeededFiles();
    assertEquals(c("old content"), vcs.getEntry("root/file").getContent());

    updater.processFile(fileContentOf(file));
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  private FileContent fileContentOf(VirtualFile f) {
    return CacheUpdaterHelper.fileContentOf(f);
  }
}

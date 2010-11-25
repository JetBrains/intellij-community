package com.intellij.openapi.vfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;

import java.io.*;

/**
 * @author yole
 */
public class RefreshChildrenTest extends IdeaTestCase {
  private File testDir;

  @Override
  protected void setUp() throws Exception {

    // the superclass sets tmpdir on every run and cleans up, but we want to do it our way

    String baseTempDir = FileUtil.getTempDirectory();
    testDir = new File(baseTempDir, "RefreshChildrenTest." + getName());

    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(testDir);
    super.tearDown();
  }

  public void testRefreshSeesLatestDirectoryContents() throws Exception {
    assertFalse(testDir.exists());
    testDir.mkdir();
    assertTrue(testDir.isDirectory());
    writeFile(testDir, "Foo.java", "");

    LocalFileSystem local = LocalFileSystem.getInstance();
    VirtualFile virtualDir = local.findFileByIoFile(testDir);
    virtualDir.getChildren();
    virtualDir.refresh(false, true);

    checkChildCount(virtualDir, 1);

    writeFile(testDir, "Bar.java", "");
    virtualDir.refresh(false, true);

    checkChildCount(virtualDir, 2);
  }

  private void writeFile(File dir, String filename, String contents) throws IOException {
    Writer writer = new OutputStreamWriter(new FileOutputStream(new File(dir, filename)), "UTF-8");
    writer.write(contents);
    writer.close();
  }

  private void checkChildCount(VirtualFile virtualDir, int expectedCount) {
    VirtualFile[] children = virtualDir.getChildren();
    if (children.length != expectedCount) {
      System.err.println("children:");
      for (VirtualFile child : children) {
        System.err.println(child.getPath());
      }
    }
    assertEquals(expectedCount, children.length);
  }

}

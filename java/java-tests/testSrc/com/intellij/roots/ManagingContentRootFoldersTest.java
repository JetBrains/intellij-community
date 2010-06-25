package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

public class ManagingContentRootFoldersTest extends IdeaTestCase {
  private VirtualFile root;
  private ContentEntry entry;
  private ModifiableRootModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        initContentRoot();
        initModifiableModel();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    if (myModel != null && myModel.isWritable()) {
      myModel.dispose();
    }
    myModel = null;
    super.tearDown();
  }

  private void initContentRoot() {
    try {
      File dir = createTempDirectory();
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
      PsiTestUtil.addContentRoot(myModule, root);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initModifiableModel() {
    myModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    for (ContentEntry e : myModel.getContentEntries()) {
      if (e.getFile() == root) entry = e;
    }
  }

  public void testCreationOfSourceFolderWithFile() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(dir, false);
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());

    dir.delete(null);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }


  public void testCreationOfSourceFolderWithUrl() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();
    dir.delete(null);

    ContentFolder f = entry.addSourceFolder(url, false);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfSourceFolderWithUrlWhenFileExists() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(url, false);
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithFile() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(dir);
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());

    dir.delete(null);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithUrl() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();
    dir.delete(null);

    ContentFolder f = entry.addExcludeFolder(url);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithUrlWhenFileExists() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(url);
    assertSame(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }
}

package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;

public class ManagingContentRootsTest extends IdeaTestCase {
  private VirtualFile dir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          LocalFileSystem fs = LocalFileSystem.getInstance();
          dir = fs.refreshAndFindFileByIoFile(createTempDirectory());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void testCreationOfContentRootWithFile() throws IOException {
    VirtualFile root = dir.createChildDirectory(null, "root");
    String url = root.getUrl();

    PsiTestUtil.addContentRoot(myModule, root);


    assertEquals(root, findContentEntry(url).getFile());

    root.delete(null);
    assertNotNull(findContentEntry(url));

    root = dir.createChildDirectory(null, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testCreationOfContentRootWithUrl() throws IOException {
    VirtualFile root = dir.createChildDirectory(null, "root");
    final String url = root.getUrl();
    root.delete(null);

    addContentRoot(url);

    assertNotNull(findContentEntry(url));

    root = dir.createChildDirectory(null, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  private void addContentRoot(final String url) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ModifiableRootModel m = getRootManager().getModifiableModel();
        m.addContentEntry(url);
        m.commit();
      }
    });
  }

  public void testCreationOfContentRootWithUrlWhenFileExists() throws IOException {
    VirtualFile root = dir.createChildDirectory(null, "root");
    final String url = root.getUrl();

    addContentRoot(url);


    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testGettingModifiableModelCorrectlySetsRootModelForContentEntries() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiTestUtil.addContentRoot(myModule, dir);

        ModifiableRootModel m = getRootManager().getModifiableModel();
        ContentEntry e = findContentEntry(dir.getUrl(), m);

        assertSame(m, ((ContentEntryImpl)e).getRootModel());
        m.dispose();
      }
    });
  }

  private ContentEntry findContentEntry(String url) {
    return findContentEntry(url, getRootManager());
  }

  private static ContentEntry findContentEntry(String url, ModuleRootModel m) {
    for (ContentEntry e : m.getContentEntries()) {
      if (e.getUrl().equals(url)) return e;
    }
    return null;
  }

  private ModuleRootManager getRootManager() {
    return ModuleRootManager.getInstance(myModule);
  }
}

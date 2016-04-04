/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.*;
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
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        LocalFileSystem fs = LocalFileSystem.getInstance();
        dir = fs.refreshAndFindFileByIoFile(createTempDirectory());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testCreationOfContentRootWithFile() throws IOException {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();

    PsiTestUtil.addContentRoot(myModule, root);


    assertEquals(root, findContentEntry(url).getFile());

    delete(root);
    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testCreationOfContentRootWithUrl() throws IOException {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();
    String path = root.getPath();
    delete(root);

    addContentRoot(path);

    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testCreationOfContentRootWithUrlWhenFileExists() throws IOException {
    VirtualFile root = createChildDirectory(dir, "root");
    addContentRoot(root.getPath());
    assertEquals(root, findContentEntry(root.getUrl()).getFile());
  }

  public void testGettingModifiableModelCorrectlySetsRootModelForContentEntries() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiTestUtil.addContentRoot(myModule, dir);

      ModifiableRootModel m = getRootManager().getModifiableModel();
      ContentEntry e = findContentEntry(dir.getUrl(), m);

      assertSame(m, ((ContentEntryImpl)e).getRootModel());
      m.dispose();
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

  private void addContentRoot(final String path) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleRootModificationUtil.addContentRoot(getModule(), path);
    });
  }

  private ModuleRootManager getRootManager() {
    return ModuleRootManager.getInstance(myModule);
  }
}
